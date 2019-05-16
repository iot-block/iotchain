package jbok.core.ledger

import cats.data.OptionT
import cats.effect.{Sync, Timer}
import cats.implicits._
import io.circe.syntax._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.common.metrics.Metrics
import jbok.core.config.GenesisConfig
import jbok.core.models._
import jbok.core.store._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.evm.WorldState
import jbok.persistent.{KeyValueDB, StageKeyValueDB}
import scodec.bits._

final class HistoryImpl[F[_]](db: KeyValueDB[F], metrics: Metrics[F])(implicit F: Sync[F], c: BigInt, T: Timer[F]) extends History[F] {
  private[this] val log = Logger[F]

  override val chainId: BigInt = c

// init
  override def initGenesis(config: GenesisConfig): F[Unit] =
    for {
      _ <- getBlockHeaderByNumber(0)
        .map(_.isDefined)
        .ifM(F.raiseError(new Exception("genesis already defined")), F.unit)
      _     <- log.d(s"init with genesis config:\n${config.asJson.spaces2}")
      state <- getWorldState()
      world <- config.alloc.toList
        .foldLeft(state) {
          case (acc, (addr, balance)) =>
            acc.putAccount(addr, Account(0, UInt256(balance)))
        }
        .persisted
      block = Block(config.header.copy(stateRoot = world.stateRootHash), config.body)
      _ <- putBlockAndReceipts(block, Nil, block.header.difficulty, asBestBlock = true)
    } yield ()

  // header
  override def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]] = metrics.timeF("getBlockHeaderByHash") {
    db.getOpt[ByteVector, BlockHeader](hash, namespaces.BlockHeader)
  }

  override def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]] = metrics.timeF("getBlockHeaderByNumber") {
    val p = for {
      hash   <- OptionT(getHashByBlockNumber(number))
      header <- OptionT(getBlockHeaderByHash(hash))
    } yield header
    p.value
  }

  override def putBlockHeader(blockHeader: BlockHeader, updateTD: Boolean): F[Unit] = metrics.timeF("putBlockHeader") {
    if (updateTD) {
      if (blockHeader.number == 0) {
        db.put(blockHeader.hash, blockHeader, namespaces.BlockHeader) >>
          db.put(blockHeader.number, blockHeader.hash, namespaces.NumberHash) >>
          db.put(blockHeader.hash, blockHeader.difficulty, namespaces.TotalDifficulty)
      } else {
        getTotalDifficultyByHash(blockHeader.parentHash).flatMap {
          case Some(td) =>
            db.put(blockHeader.hash, blockHeader, namespaces.BlockHeader) >>
              db.put(blockHeader.number, blockHeader.hash, namespaces.NumberHash) >>
              db.put(blockHeader.hash, td + blockHeader.difficulty, namespaces.TotalDifficulty)

          case None =>
            db.put(blockHeader.hash, blockHeader, namespaces.BlockHeader) >>
              db.put(blockHeader.number, blockHeader.hash, namespaces.NumberHash)
        }
      }
    } else {
      db.put(blockHeader.hash, blockHeader, namespaces.BlockHeader) >>
        db.put(blockHeader.number, blockHeader.hash, namespaces.NumberHash)
    }
  }

  // body
  override def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]] = metrics.timeF("getBlockBodyByHash") {
    db.getOpt[ByteVector, BlockBody](hash, namespaces.BlockBody)
  }

  override def putBlockBody(blockHash: ByteVector, blockBody: BlockBody): F[Unit] = metrics.timeF("putBlockBody") {
    db.put(blockHash, blockBody, namespaces.BlockBody) >>
      blockBody.transactionList.zipWithIndex
        .map {
          case (tx, index) =>
            db.put(tx.hash, TransactionLocation(blockHash, index), namespaces.TxLocation)
        }
        .sequence
        .void
  }

  // receipts
  override def getReceiptsByHash(blockhash: ByteVector): F[Option[List[Receipt]]] = metrics.timeF("getReceiptsByHash") {
    db.getOpt[ByteVector, List[Receipt]](blockhash, namespaces.Receipts)
  }

  override def putReceipts(blockHash: ByteVector, receipts: List[Receipt]): F[Unit] = metrics.timeF("putReceipts") {
    db.put(blockHash, receipts, namespaces.Receipts)
  }

  // block
  override def getBlockByHash(hash: ByteVector): F[Option[Block]] = metrics.timeF("getBlockByHash") {
    val p = for {
      header <- OptionT(getBlockHeaderByHash(hash))
      body   <- OptionT(getBlockBodyByHash(hash))
    } yield Block(header, body)

    p.value
  }

  override def getBlockByNumber(number: BigInt): F[Option[Block]] = metrics.timeF("getBlockByNumber") {
    val p = for {
      hash  <- OptionT(getHashByBlockNumber(number))
      block <- OptionT(getBlockByHash(hash))
    } yield block
    p.value
  }

  override def putBlockAndReceipts(block: Block, receipts: List[Receipt], totalDifficulty: BigInt, asBestBlock: Boolean): F[Unit] =
    putBlockHeader(block.header, updateTD = true) >>
      putBlockBody(block.header.hash, block.body) >>
      putReceipts(block.header.hash, receipts) >>
      (if (asBestBlock) {
         putBestBlockNumber(block.header.number) <* metrics.current("BestBlockNumber")(block.header.number.toDouble)
       } else {
         F.unit
       })

  override def delBlock(blockHash: ByteVector, parentAsBestBlock: Boolean): F[Unit] = metrics.timeF("delBlock") {
    val maybeBlockHeader = getBlockHeaderByHash(blockHash)
    val maybeTxList      = getBlockBodyByHash(blockHash).map(_.map(_.transactionList))

    db.del(blockHash, namespaces.BlockHeader) >>
      db.del(blockHash, namespaces.BlockBody) >>
      db.del(blockHash, namespaces.TotalDifficulty) >>
      db.del(blockHash, namespaces.Receipts) >>
      maybeTxList.map {
        case Some(txs) => txs.traverse(tx => db.del(tx.hash, namespaces.TxLocation)).void
        case None      => F.unit
      } >>
      maybeBlockHeader.map {
        case Some(bh) =>
          val removeMapping = getHashByBlockNumber(bh.number).flatMap {
            case Some(_) => db.del(bh.number, namespaces.NumberHash)
            case None    => F.unit
          }

          val updateBest =
            if (parentAsBestBlock) putBestBlockNumber(bh.number - 1)
            else F.unit

          removeMapping >> updateBest

        case None => F.unit
      }
  }

  // accounts, storage and codes
  override def getMptNode(hash: ByteVector): F[Option[ByteVector]] =
    db.getRaw(namespaces.Node ++ hash)

  override def putMptNode(hash: ByteVector, bytes: ByteVector): F[Unit] =
    db.putRaw(namespaces.Node ++ hash, bytes)

  override def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]] = metrics.timeF("getAccount") {
    val p = for {
      header  <- OptionT(getBlockHeaderByNumber(blockNumber))
      mpt     <- OptionT.liftF(MerklePatriciaTrie[F](namespaces.Node, db, Some(header.stateRoot)))
      account <- OptionT(mpt.getOpt[Address, Account](address, namespaces.empty))
    } yield account
    p.value
  }

  override def getStorage(rootHash: ByteVector, position: BigInt): F[ByteVector] = metrics.timeF("getStorage") {
    for {
      mpt   <- MerklePatriciaTrie[F](namespaces.Node, db, Some(rootHash))
      bytes <- mpt.getOpt[UInt256, UInt256](UInt256(position), namespaces.empty).map(_.getOrElse(UInt256.Zero).bytes)
    } yield bytes
  }

  override def getCode(hash: ByteVector): F[Option[ByteVector]] = metrics.timeF("getCode") {
    db.getRaw(namespaces.Code ++ hash)
  }

  override def putCode(hash: ByteVector, code: ByteVector): F[Unit] = metrics.timeF("putCode") {
    db.putRaw(namespaces.Code ++ hash, code)
  }

  override def getWorldState(
      accountStartNonce: UInt256,
      stateRootHash: Option[ByteVector],
      noEmptyAccounts: Boolean
  ): F[WorldState[F]] =
    for {
      mpt <- MerklePatriciaTrie[F](namespaces.Node, db, stateRootHash)
      accountProxy = StageKeyValueDB[F, Address, Account](namespaces.empty, mpt)
    } yield
      WorldState[F](
        db,
        this,
        accountProxy,
        stateRootHash.getOrElse(MerklePatriciaTrie.emptyRootHash),
        Set.empty,
        Map.empty,
        Map.empty,
        accountStartNonce,
        noEmptyAccounts
      )

  // helpers
  override def getTotalDifficultyByNumber(blockNumber: BigInt): F[Option[BigInt]] = {
    val p = for {
      hash <- OptionT(getHashByBlockNumber(blockNumber))
      td   <- OptionT(getTotalDifficultyByHash(hash))
    } yield td

    p.value
  }

  override def getTotalDifficultyByHash(blockHash: ByteVector): F[Option[BigInt]] =
    db.getOpt[ByteVector, BigInt](blockHash, namespaces.TotalDifficulty)

  override def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]] =
    db.getOpt[BigInt, ByteVector](number, namespaces.NumberHash)

  override def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]] =
    db.getOpt[ByteVector, TransactionLocation](txHash, namespaces.TxLocation)

  override def getBestHeader: F[BlockHeader] =
    (getBestBlockNumber >>= getBlockHeaderByNumber).flatMap {
      case Some(header) => F.pure(header)
      case None         => F.raiseError(new Exception(s"best header does not exist"))
    }

  override def getBestBlock: F[Block] =
    getBestBlockNumber.flatMap(bn =>
      getBlockByNumber(bn).flatMap {
        case Some(block) => F.pure(block)
        case None        => F.raiseError(new Exception(s"best block at ${bn} does not exist"))
    })

  override def getBestBlockNumber: F[BigInt] =
    db.getOptT[String, BigInt]("BestBlockNumber", namespaces.AppStateNamespace).getOrElse(BigInt(0))

  override def putBestBlockNumber(number: BigInt): F[Unit] =
    db.put[String, BigInt]("BestBlockNumber", number, namespaces.AppStateNamespace)

  override def genesisHeader: F[BlockHeader] =
    getBlockHeaderByNumber(0).flatMap(opt => F.fromOption(opt, new Exception("genesis is empty")))
}
