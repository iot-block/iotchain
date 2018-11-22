package jbok.core.ledger

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.config.GenesisConfig
import jbok.core.models._
import jbok.core.store._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.evm.WorldState
import jbok.persistent.{KeyValueDB, StageKeyValueDB}
import scodec.bits._

abstract class History[F[_]](val db: KeyValueDB[F], val chainId: Int) {
  // init
  def init(config: GenesisConfig = GenesisConfig.default): F[Unit]

  // header
  def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]]

  def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]]

  def putBlockHeader(blockHeader: BlockHeader): F[Unit]

  // body
  def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]]

  def putBlockBody(blockHash: ByteVector, blockBody: BlockBody): F[Unit]

  // receipts
  def getReceiptsByHash(blockhash: ByteVector): F[Option[List[Receipt]]]

  def putReceipts(blockHash: ByteVector, receipts: List[Receipt]): F[Unit]

  // block
  def getBlockByHash(hash: ByteVector): F[Option[Block]]

  def getBlockByNumber(number: BigInt): F[Option[Block]]

  def putBlock(block: Block): F[Unit]

  def putBlockAndReceipts(block: Block, receipts: List[Receipt], totalDifficulty: BigInt, asBestBlock: Boolean): F[Unit]

  def delBlock(hash: ByteVector, parentAsBestBlock: Boolean): F[Unit]

  // accounts, storage and codes
  def getMptNode(hash: ByteVector): F[Option[ByteVector]]

  def putMptNode(hash: ByteVector, bytes: ByteVector): F[Unit]

  def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]]

  def getStorage(rootHash: ByteVector, position: BigInt): F[ByteVector]

  def getCode(codeHash: ByteVector): F[Option[ByteVector]]

  def putCode(hash: ByteVector, evmCode: ByteVector): F[Unit]

  def getWorldState(
      accountStartNonce: UInt256 = UInt256.Zero,
      stateRootHash: Option[ByteVector] = None,
      noEmptyAccounts: Boolean = false
  ): F[WorldState[F]]

  // helpers
  def getTotalDifficultyByHash(blockHash: ByteVector): F[Option[BigInt]]

  def getTotalDifficultyByNumber(blockNumber: BigInt): F[Option[BigInt]]

  def putDifficulty(blockHash: ByteVector, totalDifficulty: BigInt): F[Unit]

  def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]]

  def getBestBlockNumber: F[BigInt]

  def getEstimatedHighestBlock: F[BigInt]

  def getSyncStartingBlock: F[BigInt]

  def getBestBlock: F[Block]

  def putBestBlockNumber(number: BigInt): F[Unit]

  def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]]

  def genesisHeader: F[BlockHeader]
}

object History {
  def apply[F[_]: Sync](db: KeyValueDB[F], chainId: Int = 1): F[History[F]] =
    Sync[F].pure {
      new HistoryImpl[F](
        db,
        chainId
      )
    }
}

class HistoryImpl[F[_]](
    db: KeyValueDB[F],
    chainId: Int
)(implicit F: Sync[F])
    extends History[F](db, chainId) {

  private val appStateStore = new AppStateStore[F](db)

  // init
  override def init(config: GenesisConfig): F[Unit] =
    for {
      _ <- getBlockHeaderByNumber(0)
        .map(_.isDefined)
        .ifM(F.raiseError(new Exception("genesis already defined")), F.unit)
      state <- getWorldState()
      world <- config.alloc.toList
        .foldLeft(state) {
          case (acc, (addr, balance)) =>
            acc.putAccount(Address(ByteVector.fromValidHex(addr)), Account(0, UInt256(balance)))
        }
        .persisted
      block = Block(config.header.copy(stateRoot = world.stateRootHash), config.body)
      _ <- putBlockAndReceipts(block, Nil, block.header.difficulty, asBestBlock = true)
    } yield ()

  // header
  override def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]] =
    db.getOpt[ByteVector, BlockHeader](hash, namespaces.BlockHeader)

  override def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]] = {
    val p = for {
      hash   <- OptionT(getHashByBlockNumber(number))
      header <- OptionT(getBlockHeaderByHash(hash))
    } yield header
    p.value
  }

  override def putBlockHeader(blockHeader: BlockHeader): F[Unit] = {
    val hash = blockHeader.hash
    db.put(hash, blockHeader, namespaces.BlockHeader) *> db.put(blockHeader.number, hash, namespaces.NumberHash)
  }

  // body
  override def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]] =
    db.getOpt[ByteVector, BlockBody](hash, namespaces.BlockBody)

  override def putBlockBody(blockHash: ByteVector, blockBody: BlockBody): F[Unit] =
    db.put(blockHash, blockBody, namespaces.BlockBody) *>
      blockBody.transactionList.zipWithIndex
        .map {
          case (tx, index) =>
            db.put(tx.hash, TransactionLocation(blockHash, index), namespaces.TxLocation)
        }
        .sequence
        .void

  // receipts
  override def getReceiptsByHash(blockhash: ByteVector): F[Option[List[Receipt]]] =
    db.getOpt[ByteVector, List[Receipt]](blockhash, namespaces.Receipts)

  override def putReceipts(blockHash: ByteVector, receipts: List[Receipt]): F[Unit] =
    db.put(blockHash, receipts, namespaces.Receipts)

  // block
  override def getBlockByHash(hash: ByteVector): F[Option[Block]] = {
    val p = for {
      header <- OptionT(getBlockHeaderByHash(hash))
      body   <- OptionT(getBlockBodyByHash(hash))
    } yield Block(header, body)

    p.value
  }

  override def getBlockByNumber(number: BigInt): F[Option[Block]] = {
    val p = for {
      hash  <- OptionT(getHashByBlockNumber(number))
      block <- OptionT(getBlockByHash(hash))
    } yield block
    p.value
  }

  override def putBlockAndReceipts(block: Block,
                                   receipts: List[Receipt],
                                   totalDifficulty: BigInt,
                                   asBestBlock: Boolean): F[Unit] =
    putBlock(block) *>
      putReceipts(block.header.hash, receipts) *>
      putDifficulty(block.header.hash, totalDifficulty) *>
      (if (asBestBlock) {
         putBestBlockNumber(block.header.number)
       } else {
         F.unit
       })

  override def putBlock(block: Block): F[Unit] =
    putBlockHeader(block.header) *> putBlockBody(block.header.hash, block.body)

  override def delBlock(blockHash: ByteVector, parentAsBestBlock: Boolean): F[Unit] = {
    val maybeBlockHeader = getBlockHeaderByHash(blockHash)
    val maybeTxList      = getBlockBodyByHash(blockHash).map(_.map(_.transactionList))

    db.del(blockHash, namespaces.BlockHeader) *>
      db.del(blockHash, namespaces.BlockBody) *>
      db.del(blockHash, namespaces.TotalDifficulty) *>
      db.del(blockHash, namespaces.Receipts) *>
      maybeTxList.map {
        case Some(txs) => txs.traverse(tx => db.del(tx.hash, namespaces.TxLocation)).void
        case None      => F.unit
      } *>
      maybeBlockHeader.map {
        case Some(bh) =>
          val removeMapping = getHashByBlockNumber(bh.number).flatMap {
            case Some(_) => db.del(bh.number, namespaces.NumberHash)
            case None    => F.unit
          }

          val updateBest =
            if (parentAsBestBlock) appStateStore.putBestBlockNumber(bh.number - 1)
            else F.unit

          removeMapping *> updateBest

        case None => F.unit
      }
  }

  // accounts, storage and codes
  override def getMptNode(hash: ByteVector): F[Option[ByteVector]] =
    db.getRaw(namespaces.Node ++ hash)

  override def putMptNode(hash: ByteVector, bytes: ByteVector): F[Unit] =
    db.putRaw(namespaces.Node ++ hash, bytes)

  override def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]] = {
    val p = for {
      header  <- OptionT(getBlockHeaderByNumber(blockNumber))
      mpt     <- OptionT.liftF(MerklePatriciaTrie[F](namespaces.Node, db, Some(header.stateRoot)))
      account <- OptionT(mpt.getOpt[Address, Account](address, namespaces.empty))
    } yield account
    p.value
  }

  override def getStorage(rootHash: ByteVector, position: BigInt): F[ByteVector] =
    for {
      mpt   <- MerklePatriciaTrie[F](namespaces.Node, db, Some(rootHash))
      bytes <- mpt.getOpt[UInt256, UInt256](UInt256(position), namespaces.empty).map(_.getOrElse(UInt256.Zero).bytes)
    } yield bytes

  override def getCode(hash: ByteVector): F[Option[ByteVector]] =
    db.getRaw(namespaces.Code ++ hash)

  override def putCode(hash: ByteVector, code: ByteVector): F[Unit] =
    db.putRaw(namespaces.Code ++ hash, code)

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

  override def putDifficulty(blockhash: ByteVector, totalDifficulty: BigInt): F[Unit] =
    db.put(blockhash, totalDifficulty, namespaces.TotalDifficulty)

  override def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]] =
    db.getOpt[BigInt, ByteVector](number, namespaces.NumberHash)

  override def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]] =
    db.getOpt[ByteVector, TransactionLocation](txHash, namespaces.TxLocation)

  override def getEstimatedHighestBlock: F[BigInt] =
    appStateStore.getEstimatedHighestBlock

  override def getSyncStartingBlock: F[BigInt] =
    appStateStore.getSyncStartingBlock

  override def getBestBlock: F[Block] =
    getBestBlockNumber.flatMap(bn =>
      getBlockByNumber(bn).flatMap {
        case Some(block) => F.pure(block)
        case None        => F.raiseError(new Exception(s"best block at ${bn} does not exist"))
    })

  override def getBestBlockNumber: F[BigInt] =
    appStateStore.getBestBlockNumber

  override def putBestBlockNumber(number: BigInt): F[Unit] =
    appStateStore.putBestBlockNumber(number)

  override def genesisHeader: F[BlockHeader] =
    getBlockHeaderByNumber(0).map(_.get)
}
