package jbok.core.ledger

import cats.data.OptionT
import cats.effect.{Sync, Timer}
import cats.implicits._
import io.circe.syntax._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.common.metrics.Metrics
import jbok.common.metrics.implicits._
import jbok.core.config.GenesisConfig
import jbok.core.models._
import jbok.core.store._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.evm.WorldState
import jbok.persistent.{KeyValueDB, StageKeyValueDB}
import scodec.bits._

final class HistoryImpl[F[_]](db: KeyValueDB[F], metrics: Metrics[F])(implicit F: Sync[F], c: BigInt, T: Timer[F]) extends History[F] {
  private[this] val log = Logger[F]

  implicit private val m = metrics

  override val chainId: BigInt = c

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
      _ <- putBlockAndReceipts(block, Nil, block.header.difficulty)
    } yield ()

  // header
  override def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]] =
    db.getOpt[ByteVector, BlockHeader](hash, namespaces.BlockHeader).observed("getBlockHeaderByHash")

  override def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]] =
    (for {
      hash   <- OptionT(getHashByBlockNumber(number))
      header <- OptionT(getBlockHeaderByHash(hash))
    } yield header).value.observed("getBlockHeaderByNumber")

  override def putBlockHeader(blockHeader: BlockHeader): F[Unit] = metrics.observed("putBlockHeader") {
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
  }

  // body
  override def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]] =
    db.getOpt[ByteVector, BlockBody](hash, namespaces.BlockBody).observed("getBlockBodyByHash")

  override def putBlockBody(blockHash: ByteVector, blockBody: BlockBody): F[Unit] =
    (db.put(blockHash, blockBody, namespaces.BlockBody) >>
      blockBody.transactionList.zipWithIndex
        .traverse_ {
          case (tx, index) =>
            db.put(tx.hash, TransactionLocation(blockHash, index), namespaces.TxLocation)
        }).observed("putBlockBody")

  // receipts
  override def getReceiptsByHash(blockHash: ByteVector): F[Option[List[Receipt]]] =
    db.getOpt[ByteVector, List[Receipt]](blockHash, namespaces.Receipts).observed("getReceiptsByHash")

  override def putReceipts(blockHash: ByteVector, receipts: List[Receipt]): F[Unit] =
    db.put(blockHash, receipts, namespaces.Receipts).observed("putReceipts")

  // block
  override def getBlockByHash(hash: ByteVector): F[Option[Block]] =
    (for {
      header <- OptionT(getBlockHeaderByHash(hash))
      body   <- OptionT(getBlockBodyByHash(hash))
    } yield Block(header, body)).value.observed("getBlockByHash")

  override def getBlockByNumber(number: BigInt): F[Option[Block]] =
    (for {
      hash  <- OptionT(getHashByBlockNumber(number))
      block <- OptionT(getBlockByHash(hash))
    } yield block).value.observed("getBlockByNumber")

  override def putBlockAndReceipts(block: Block, receipts: List[Receipt], totalDifficulty: BigInt): F[Unit] =
    (putBlockHeader(block.header) >>
      putBlockBody(block.header.hash, block.body) >>
      putReceipts(block.header.hash, receipts) >>
      putBestBlockNumber(block.header.number)).observed("putBlockAndReceipts")

  override def delBlock(blockHash: ByteVector, parentAsBestBlock: Boolean): F[Unit] = {
    val maybeBlockHeader = getBlockHeaderByHash(blockHash)
    val maybeTxList      = getBlockBodyByHash(blockHash).map(_.map(_.transactionList))

    val p = db.del(blockHash, namespaces.BlockHeader) >>
      db.del(blockHash, namespaces.BlockBody) >>
      db.del(blockHash, namespaces.TotalDifficulty) >>
      db.del(blockHash, namespaces.Receipts) >>
      maybeTxList.flatMap {
        case Some(txs) => txs.traverse(tx => db.del(tx.hash, namespaces.TxLocation)).void
        case None      => F.unit
      } >>
      maybeBlockHeader.flatMap {
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

    p.observed("delBlock")
  }

  // accounts, storage and codes
  override def getMptNode(hash: ByteVector): F[Option[ByteVector]] =
    db.getRaw(namespaces.Node ++ hash).observed("getMptNode")

  override def putMptNode(hash: ByteVector, bytes: ByteVector): F[Unit] =
    db.putRaw(namespaces.Node ++ hash, bytes).observed("putMptNode")

  override def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]] =
    (for {
      header  <- OptionT(getBlockHeaderByNumber(blockNumber))
      mpt     <- OptionT.liftF(MerklePatriciaTrie[F](namespaces.Node, db, Some(header.stateRoot)))
      account <- OptionT(mpt.getOpt[Address, Account](address, namespaces.empty))
    } yield account).value.observed("getAccount")

  override def getStorage(rootHash: ByteVector, position: BigInt): F[ByteVector] =
    (for {
      mpt   <- MerklePatriciaTrie[F](namespaces.Node, db, Some(rootHash))
      bytes <- mpt.getOpt[UInt256, UInt256](UInt256(position), namespaces.empty).map(_.getOrElse(UInt256.Zero).bytes)
    } yield bytes).observed("getStorage")

  override def getCode(hash: ByteVector): F[Option[ByteVector]] =
    db.getRaw(namespaces.Code ++ hash).observed("getCode")

  override def putCode(hash: ByteVector, code: ByteVector): F[Unit] =
    db.putRaw(namespaces.Code ++ hash, code).observed("putCode")

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
  override def getTotalDifficultyByNumber(blockNumber: BigInt): F[Option[BigInt]] =
    (for {
      hash <- OptionT(getHashByBlockNumber(blockNumber))
      td   <- OptionT(getTotalDifficultyByHash(hash))
    } yield td).value.observed("getTotalDifficultyByNumber")

  override def getTotalDifficultyByHash(blockHash: ByteVector): F[Option[BigInt]] =
    db.getOpt[ByteVector, BigInt](blockHash, namespaces.TotalDifficulty).observed("getTotalDifficultyByHash")

  override def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]] =
    db.getOpt[BigInt, ByteVector](number, namespaces.NumberHash).observed("getHashByBlockNumber")

  override def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]] =
    db.getOpt[ByteVector, TransactionLocation](txHash, namespaces.TxLocation).observed("getTransactionLocation")

  override def getBestBlock: F[Block] =
    getBestBlockNumber.flatMap(bn =>
      getBlockByNumber(bn).flatMap {
        case Some(block) => F.pure(block)
        case None        => F.raiseError(new Exception(s"best block at ${bn} does not exist"))
    })

  override def getBestBlockNumber: F[BigInt] =
    db.getOptT[String, BigInt]("BestBlockNumber", namespaces.AppStateNamespace).getOrElse(BigInt(0)).observed("getBestBlockNumber")

  override def putBestBlockNumber(number: BigInt): F[Unit] =
    db.put[String, BigInt]("BestBlockNumber", number, namespaces.AppStateNamespace)
      .observed("putBestBlockNumber") <* metrics.set("BestBlockNumber")(number.toDouble)

  override def genesisHeader: F[BlockHeader] =
    getBlockHeaderByNumber(0).flatMap(opt => F.fromOption(opt, new Exception("genesis is empty")))
}
