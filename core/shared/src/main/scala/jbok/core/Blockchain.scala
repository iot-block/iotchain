package jbok.core

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import jbok.core.genesis.Genesis
import jbok.core.models._
import jbok.core.store._
import jbok.crypto.authds.mpt.{MPTrie, Node}
import jbok.persistent.KeyValueDB
import scodec.bits._

abstract class Blockchain[F[_]](implicit F: Sync[F]) {
  protected val log = org.log4s.getLogger

  def loadGenesis: F[Unit] = {
    log.info(s"loading genesis data")

    for {
      headerOpt <- getBlockHeaderByNumber(0)
      _ <- headerOpt match {
        case Some(h) if h.hash == Genesis.header.hash =>
          log.info("genesis data already in the database")
          F.unit

        case Some(h) =>
          F.raiseError(
            new RuntimeException("Genesis data present in the database does not match genesis block from file")
          )

        case None =>
          save(Genesis.block, Nil, saveAsBestBlock = true)
      }
    } yield ()
  }

  /**
    * Allows to query a blockHeader by block hash
    *
    * @param hash of the block that's being searched
    * @return [[BlockHeader]] if found
    */
  def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]]

  def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]] = {
    val p = for {
      hash <- OptionT(getHashByBlockNumber(number))
      header <- OptionT(getBlockHeaderByHash(hash))
    } yield header
    p.value
  }

  /**
    * Allows to query a blockBody by block hash
    *
    * @param hash of the block that's being searched
    * @return [[BlockBody]] if found
    */
  def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]]

  /**
    * Allows to query for a block based on it's hash
    *
    * @param hash of the block that's being searched
    * @return Block if found
    */
  def getBlockByHash(hash: ByteVector): F[Option[Block]] = {
    val p = for {
      header <- OptionT(getBlockHeaderByHash(hash))
      body <- OptionT(getBlockBodyByHash(hash))
    } yield Block(header, body)

    p.value
  }

  /**
    * Allows to query for a block based on it's number
    *
    * @param number Block number
    * @return Block if it exists
    */
  def getBlockByNumber(number: BigInt): F[Option[Block]] = {
    val p = for {
      hash <- OptionT(getHashByBlockNumber(number))
      block <- OptionT(getBlockByHash(hash))
    } yield block
    p.value
  }

  /**
    * Get an account for an address and a block number
    *
    * @param address address of the account
    * @param blockNumber the block that determines the state of the account
    */
  def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]]

  /**
    * Get account storage at given position
    *
    * @param rootHash storage root hash
    * @param position storage position
    */
  def getAccountStorageAt(rootHash: ByteVector, position: BigInt): F[ByteVector]

  /**
    * Returns the receipts based on a block hash
    * @param blockhash
    * @return Receipts if found
    */
  def getReceiptsByHash(blockhash: ByteVector): F[Option[List[Receipt]]]

  /**
    * Returns EVM code searched by it's hash
    * @param hash Code Hash
    * @return EVM code if found
    */
  def getEvmCodeByHash(hash: ByteVector): Option[ByteVector]

  /**
    * Returns MPT node searched by it's hash
    * @param hash Node Hash
    * @return MPT node
    */
  def getMptNodeByHash(hash: ByteVector): F[Node]

  /**
    * Returns the total difficulty based on a block hash
    * @param blockhash
    * @return total difficulty if found
    */
  def getTotalDifficultyByHash(blockhash: ByteVector): F[Option[BigInt]]

  def getTotalDifficultyByNumber(blockNumber: BigInt): F[Option[BigInt]] = {
    val p = for {
      hash <- OptionT(getHashByBlockNumber(blockNumber))
      td <- OptionT(getTotalDifficultyByHash(hash))
    } yield td

    p.value
  }

  def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]]

  def getBestBlockNumber: F[BigInt]

  def getBestBlock: F[Block]

  /**
    * Persists full block along with receipts and total difficulty
    * @param saveAsBestBlock - whether to save the block's number as current best block
    */
  def save(block: Block, receipts: List[Receipt], saveAsBestBlock: Boolean): F[Unit] =
    save(block) *>
      save(block.header.hash, receipts) *>
      (if (saveAsBestBlock) {
         saveBestBlockNumber(block.header.number)
       } else {
         F.unit
       }) *>
      pruneState(block.header.number)

  /**
    * Persists a block in the underlying Blockchain Database
    *
    * @param block Block to be saved
    */
  def save(block: Block): F[Unit] =
    save(block.header) *> save(block.header.hash, block.body)

  def removeBlock(hash: ByteVector, saveParentAsBestBlock: Boolean): F[Unit]

  /**
    * Persists a block header in the underlying Blockchain Database
    *
    * @param blockHeader Block to be saved
    */
  def save(blockHeader: BlockHeader): F[Unit]

  def save(blockHash: ByteVector, blockBody: BlockBody): F[Unit]

  def save(blockHash: ByteVector, receipts: List[Receipt]): F[Unit]

  def save(hash: ByteVector, evmCode: ByteVector): F[Unit]

  def save(blockHash: ByteVector, totalDifficulty: BigInt): F[Unit]

  def saveBestBlockNumber(number: BigInt): F[Unit]

//  def saveNode(nodeHash: ByteVector, nodeEncoded: ByteVector, blockNumber: BigInt): F[Unit]

  def saveAccount(address: Address, account: Account): F[Unit]

  /**
    * Returns a block hash given a block number
    *
    * @param number Number of the searchead block
    * @return Block hash if found
    */
  def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]]

  def genesisHeader: F[BlockHeader] = getBlockHeaderByNumber(0).map(_.get)

  def genesisBlock: F[Block] = getBlockByNumber(0).map(_.get)

//  def getWorldStateProxy(blockNumber: BigInt,
//                         accountStartNonce: UInt256,
//                         stateRootHash: Option[ByteVector] = None,
//                         noEmptyAccounts: Boolean = false): WS
//
//  def getReadOnlyWorldStateProxy(blockNumber: Option[BigInt],
//                                 accountStartNonce: UInt256,
//                                 stateRootHash: Option[ByteVector] = None,
//                                 noEmptyAccounts: Boolean = false): WS

  def pruneState(blockNumber: BigInt): F[Unit]

  def rollbackStateChangesMadeByBlock(blockNumber: BigInt): F[Unit]
}

object Blockchain {
  def inMemory[F[_]: Sync]: F[Blockchain[F]] =
    for {
      db <- KeyValueDB.inMemory[F]
      blockchain <- apply[F](db)
      _ <- blockchain.loadGenesis
    } yield blockchain

  def apply[F[_]: Sync](db: KeyValueDB[F]): F[Blockchain[F]] = {
    val headerStore = new BlockHeaderStore[F](db)
    val bodyStore = new BlockBodyStore[F](db)
    val receiptStore = new ReceiptStore[F](db)
    val numberHashStore = new BlockNumberHashStore[F](db)
    val txLocationStore = new TransactionLocationStore[F](db)
    val appStateStore = new AppStateStore[F](db)

    MPTNodeStore[F](db).map { mptStore =>
      new BlockchainImpl[F](
        headerStore,
        bodyStore,
        receiptStore,
        numberHashStore,
        txLocationStore,
        appStateStore,
        mptStore
      )
    }
  }
}

class BlockchainImpl[F[_]](
    headerStore: BlockHeaderStore[F],
    bodyStore: BlockBodyStore[F],
    receiptStore: ReceiptStore[F],
    numberHashStore: BlockNumberHashStore[F],
    txLocationStore: TransactionLocationStore[F],
    appStateStore: AppStateStore[F],
    mptStore: MPTNodeStore[F]
)(implicit F: Sync[F])
    extends Blockchain[F] {

  /**
    * Allows to query a blockHeader by block hash
    *
    * @param hash of the block that's being searched
    * @return [[BlockHeader]] if found
    */
  override def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]] =
    headerStore.getOpt(hash)

  /**
    * Allows to query a blockBody by block hash
    *
    * @param hash of the block that's being searched
    * @return [[BlockBody]] if found
    */
  override def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]] =
    bodyStore.getOpt(hash)

  /**
    * Get account storage at given position
    *
    * @param rootHash storage root hash
    * @param position storage position
    */
  override def getAccountStorageAt(rootHash: ByteVector, position: BigInt): F[ByteVector] = ???

  /**
    * Get an account for an address and a block number
    *
    * @param address     address of the account
    * @param blockNumber the block that determines the state of the account
    */
  override def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]] =
    mptStore.getOpt(address)

  /**
    * Returns the receipts based on a block hash
    *
    * @param blockhash
    * @return Receipts if found
    */
  override def getReceiptsByHash(blockhash: ByteVector): F[Option[List[Receipt]]] =
    receiptStore.getOpt(blockhash)

  /**
    * Returns EVM code searched by it's hash
    *
    * @param hash Code Hash
    * @return EVM code if found
    */
  override def getEvmCodeByHash(hash: ByteVector): Option[ByteVector] = ???

  /**
    * Returns the total difficulty based on a block hash
    *
    * @param blockhash
    * @return total difficulty if found
    */
  override def getTotalDifficultyByHash(blockhash: ByteVector): F[Option[BigInt]] = ???

  override def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]] =
    txLocationStore.getOpt(txHash)

  override def getBestBlockNumber: F[BigInt] =
    appStateStore.getBestBlockNumber

  override def getBestBlock: F[Block] =
    getBestBlockNumber.flatMap(bn => getBlockByNumber(bn).map(_.get))

  override def removeBlock(blockHash: ByteVector, saveParentAsBestBlock: Boolean): F[Unit] = {
    val maybeBlockHeader = getBlockHeaderByHash(blockHash)
    val maybeTxList = getBlockBodyByHash(blockHash).map(_.map(_.transactionList))

    headerStore.del(blockHash) *>
      bodyStore.del(blockHash) *>
//    totalDifficultyStorage.remove(blockHash)
      receiptStore.del(blockHash) *>
      maybeTxList.map {
        case Some(txs) => txs.map(tx => txLocationStore.del(tx.hash)).sequence.void
        case None      => F.unit
      } *>
      maybeBlockHeader.map {
        case Some(bh) =>
          val rollback = rollbackStateChangesMadeByBlock(bh.number)
          val removeMapping = getHashByBlockNumber(bh.number).flatMap {
            case Some(hash) => numberHashStore.del(bh.number)
            case None       => F.unit
          }

          val updateBest =
            if (saveParentAsBestBlock) appStateStore.putBestBlockNumber(bh.number - 1)
            else F.unit

          rollback *> removeMapping *> updateBest

        case None => F.unit
      }
  }

  /**
    * Persists a block header in the underlying Blockchain Database
    *
    * @param blockHeader Block to be saved
    */
  override def save(blockHeader: BlockHeader): F[Unit] = {
    val hash = blockHeader.hash
    headerStore.put(hash, blockHeader) *> numberHashStore.put(blockHeader.number, hash)
  }

  override def save(blockHash: ByteVector, blockBody: BlockBody): F[Unit] = {
      log.info(s"saving blockBody")

    bodyStore.put(blockHash, blockBody) *>
      blockBody.transactionList.zipWithIndex
        .map {
          case (tx, index) =>
            txLocationStore.put(tx.hash, TransactionLocation(blockHash, index))
        }
        .sequence
        .void
  }

  override def save(blockHash: ByteVector, receipts: List[Receipt]): F[Unit] =
    receiptStore.put(blockHash, receipts)

  override def save(hash: ByteVector, evmCode: ByteVector): F[Unit] = ???

  override def save(blockhash: ByteVector, totalDifficulty: BigInt): F[Unit] = ???

  override def saveAccount(address: Address, account: Account): F[Unit] =
    mptStore.put(address, account)

  override def saveBestBlockNumber(number: BigInt): F[Unit] =
    appStateStore.putBestBlockNumber(number)

  /**
    * Returns a block hash given a block number
    *
    * @param number Number of the searched block
    * @return Block hash if found
    */
  override def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]] =
    numberHashStore.getOpt(number)

  override def pruneState(blockNumber: BigInt): F[Unit] = F.unit

  override def rollbackStateChangesMadeByBlock(blockNumber: BigInt): F[Unit] = ???

  /**
    * Returns MPT node searched by it's hash
    *
    * @param hash Node Hash
    * @return MPT node
    */
  override def getMptNodeByHash(hash: ByteVector): F[Node] = mptStore.getNodeByHash(hash)
}
