package jbok.core.ledger

import cats.effect.{Sync, Timer}
import jbok.common.metrics.Metrics
import jbok.core.config.GenesisConfig
import jbok.core.models._
import jbok.evm.WorldState
import jbok.persistent.KeyValueDB
import scodec.bits._

trait History[F[_]] {
  def chainId: BigInt

  // init
  def initGenesis(config: GenesisConfig): F[Unit]

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

  def putBlockAndReceipts(block: Block, receipts: List[Receipt], totalDifficulty: BigInt): F[Unit]

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
      noEmptyAccounts: Boolean = true
  ): F[WorldState[F]]

  // helpers
  def getTotalDifficultyByHash(blockHash: ByteVector): F[Option[BigInt]]

  def getTotalDifficultyByNumber(blockNumber: BigInt): F[Option[BigInt]]

  def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]]

  def getBestBlockNumber: F[BigInt]

  def getBestBlock: F[Block]

  def putBestBlockNumber(number: BigInt): F[Unit]

  def getHashByBlockNumber(number: BigInt): F[Option[ByteVector]]

  def genesisHeader: F[BlockHeader]
}

object History {
  def apply[F[_]](db: KeyValueDB[F])(implicit F: Sync[F], chainId: BigInt, T: Timer[F]): History[F] =
    new HistoryImpl[F](db, Metrics.nop[F])

  def apply[F[_]](db: KeyValueDB[F], metrics: Metrics[F])(implicit F: Sync[F], chainId: BigInt, T: Timer[F]): History[F] =
    new HistoryImpl[F](db, metrics)
}
