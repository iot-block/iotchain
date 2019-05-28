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
import jbok.persistent._
import scodec.bits._

final class HistoryImpl[F[_]](store: KVStore[F], metrics: Metrics[F])(implicit F: Sync[F], c: BigInt, T: Timer[F]) extends History[F] {
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
    store.getAs[BlockHeader](ColumnFamilies.BlockHeader, hash.asBytes).observed("getBlockHeaderByHash")

  override def getBlockHeaderByNumber(number: BigInt): F[Option[BlockHeader]] =
    (for {
      hash   <- OptionT(getHashByBlockNumber(number))
      header <- OptionT(getBlockHeaderByHash(hash))
    } yield header).value.observed("getBlockHeaderByNumber")

  override def putBlockHeader(blockHeader: BlockHeader): F[Unit] = metrics.observed("putBlockHeader") {
    if (blockHeader.number == 0) {
      val puts = List(
        Put(ColumnFamilies.BlockHeader, blockHeader.hash.asBytes, blockHeader.asBytes),
        Put(ColumnFamilies.NumberHash, blockHeader.number.asBytes, blockHeader.hash.asBytes),
        Put(ColumnFamilies.TotalDifficulty, blockHeader.hash.asBytes, blockHeader.difficulty.asBytes)
      )
      store.writeBatch(puts, Nil)
    } else {
      getTotalDifficultyByHash(blockHeader.parentHash).flatMap {
        case Some(td) =>
          val puts = List(
            Put(ColumnFamilies.BlockHeader, blockHeader.hash.asBytes, blockHeader.asBytes),
            Put(ColumnFamilies.NumberHash, blockHeader.number.asBytes, blockHeader.hash.asBytes),
            Put(ColumnFamilies.TotalDifficulty, blockHeader.hash.asBytes, (td + blockHeader.difficulty).asBytes)
          )
          store.writeBatch(puts, Nil)

        case None =>
          val puts = List(
            Put(ColumnFamilies.BlockHeader, blockHeader.hash.asBytes, blockHeader.asBytes),
            Put(ColumnFamilies.NumberHash, blockHeader.number.asBytes, blockHeader.hash.asBytes),
          )
          store.writeBatch(puts, Nil)
      }
    }
  }

  // body
  override def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]] =
    store.getAs[BlockBody](ColumnFamilies.BlockBody, hash.asBytes).observed("getBlockBodyByHash")

  override def putBlockBody(blockHash: ByteVector, blockBody: BlockBody): F[Unit] = {
    val putBody = Put(ColumnFamilies.BlockBody, blockHash.asBytes, blockBody.asBytes)
    val putLocations = blockBody.transactionList.zipWithIndex.map {
      case (tx, index) =>
        Put(ColumnFamilies.TxLocation, tx.hash.asBytes, TransactionLocation(blockHash, index).asBytes)
    }
    store.writeBatch(putBody :: putLocations, Nil).observed("putBlockBody")
  }

  // receipts
  override def getReceiptsByHash(blockHash: ByteVector): F[Option[List[Receipt]]] =
    store.getAs[List[Receipt]](ColumnFamilies.Receipts, blockHash.asBytes).observed("getReceiptsByHash")

  override def putReceipts(blockHash: ByteVector, receipts: List[Receipt]): F[Unit] =
    store.put(ColumnFamilies.Receipts, blockHash.asBytes, receipts.asBytes).observed("putReceipts")

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

  override def delBlock(blockHash: ByteVector): F[Unit] = {
    val dels: F[List[Del]] =
      List(
        Del(ColumnFamilies.BlockHeader, blockHash),
        Del(ColumnFamilies.BlockBody, blockHash),
        Del(ColumnFamilies.TotalDifficulty, blockHash),
        Del(ColumnFamilies.Receipts, blockHash)
      ).pure[F]

    val delMapping: F[List[Del]] =
      getBlockHeaderByHash(blockHash).flatMap {
        case Some(header) =>
          getHashByBlockNumber(header.number).map {
            case Some(_) => Del(ColumnFamilies.NumberHash, header.number.asBytes) :: Nil
            case None    => Nil
          }
        case None => F.pure(Nil)
      }

    val delTxs: F[List[Del]] =
      getBlockBodyByHash(blockHash).map(_.map(_.transactionList)).map {
        case Some(txs) => txs.map(tx => Del(ColumnFamilies.TxLocation, tx.hash))
        case None      => Nil
      }

    (dels, delMapping, delTxs)
      .mapN {
        case (a, b, c) =>
          a ++ b ++ c
      }
      .flatMap(dels => store.writeBatch(Nil, dels))
      .observed("delBlock")
  }

  // accounts, storage and codes
  override def getMptNode(hash: ByteVector): F[Option[ByteVector]] =
    store.getAs[ByteVector](ColumnFamilies.Node, hash.asBytes).observed("getMptNode")

  override def putMptNode(hash: ByteVector, bytes: ByteVector): F[Unit] =
    store.put(ColumnFamilies.Node, hash.asBytes, bytes.asBytes).observed("putMptNode")

  override def getAccount(address: Address, blockNumber: BigInt): F[Option[Account]] =
    (for {
      header  <- OptionT(getBlockHeaderByNumber(blockNumber))
      mpt     <- OptionT.liftF(MerklePatriciaTrie[F, Address, Account](ColumnFamilies.Node, store, Some(header.stateRoot)))
      account <- OptionT(mpt.get(address))
    } yield account).value.observed("getAccount")

  override def getStorage(rootHash: ByteVector, position: BigInt): F[ByteVector] =
    (for {
      mpt   <- MerklePatriciaTrie[F, UInt256, UInt256](ColumnFamilies.Node, store, Some(rootHash))
      bytes <- mpt.get(UInt256(position)).map(_.getOrElse(UInt256.Zero).bytes)
    } yield bytes).observed("getStorage")

  override def getCode(hash: ByteVector): F[Option[ByteVector]] =
    store.getAs[ByteVector](ColumnFamilies.Code, hash.asBytes).observed("getCode")

  override def putCode(hash: ByteVector, code: ByteVector): F[Unit] =
    store.put(ColumnFamilies.Code, hash.asBytes, code.asBytes).observed("putCode")

  override def getWorldState(
      accountStartNonce: UInt256,
      stateRootHash: Option[ByteVector],
      noEmptyAccounts: Boolean
  ): F[WorldState[F]] =
    for {
      mpt <- MerklePatriciaTrie[F, Address, Account](ColumnFamilies.Node, store, stateRootHash)
      accountProxy = StageKVStore(mpt)
    } yield
      WorldState[F](
        store,
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
    store.getAs[BigInt](ColumnFamilies.TotalDifficulty, blockHash.asBytes).observed("getTotalDifficultyByHash")

  override def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]] =
    store.getAs[ByteVector](ColumnFamilies.NumberHash, number.asBytes).observed("getHashByBlockNumber")

  override def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]] =
    store.getAs[TransactionLocation](ColumnFamilies.TxLocation, txHash.asBytes).observed("getTransactionLocation")

  override def getBestBlockHeader: F[BlockHeader] =
    getBestBlockNumber.flatMap(bn =>
      getBlockHeaderByNumber(bn).flatMap {
        case Some(header) => F.pure(header)
        case None         => F.raiseError(new Exception(s"best block header at ${bn} does not exist"))
    })

  override def getBestBlock: F[Block] =
    getBestBlockNumber.flatMap(bn =>
      getBlockByNumber(bn).flatMap {
        case Some(block) => F.pure(block)
        case None        => F.raiseError(new Exception(s"best block at ${bn} does not exist"))
    })

  override def getBestBlockNumber: F[BigInt] =
    store.getAs[BigInt](ColumnFamilies.AppState, "BestBlockNumber".asBytes).map(_.getOrElse(BigInt(0))).observed("getBestBlockNumber")

  override def putBestBlockNumber(number: BigInt): F[Unit] =
    store
      .put(ColumnFamilies.AppState, "BestBlockNumber".asBytes, number.asBytes)
      .observed("putBestBlockNumber") <* metrics.set("BestBlockNumber")(number.toDouble)

  override def genesisHeader: F[BlockHeader] =
    getBlockHeaderByNumber(0).flatMap(opt => F.fromOption(opt, new Exception("genesis is empty")))
}
