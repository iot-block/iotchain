package jbok.core.ledger

import cats.effect.{Sync, Timer}
import jbok.common.math.N
import jbok.common.metrics.Metrics
import jbok.core.config.GenesisConfig
import jbok.core.models._
import jbok.evm.WorldState
import jbok.persistent.KVStore
import scodec.bits._

trait History[F[_]] {
  def chainId: ChainId

  // init
  def initGenesis(config: GenesisConfig): F[Unit]

  // header
  def getBlockHeaderByHash(hash: ByteVector): F[Option[BlockHeader]]

  def getBlockHeaderByNumber(number: N): F[Option[BlockHeader]]

  def putBlockHeader(blockHeader: BlockHeader): F[Unit]

  // body
  def getBlockBodyByHash(hash: ByteVector): F[Option[BlockBody]]

  def putBlockBody(blockHash: ByteVector, blockBody: BlockBody): F[Unit]

  // receipts
  def getReceiptsByHash(blockhash: ByteVector): F[Option[List[Receipt]]]

  def putReceipts(blockHash: ByteVector, receipts: List[Receipt]): F[Unit]

  // block
  def getBlockByHash(hash: ByteVector): F[Option[Block]]

  def getBlockByNumber(number: N): F[Option[Block]]

  def putBlockAndReceipts(block: Block, receipts: List[Receipt], totalDifficulty: N): F[Unit]

  def delBlock(hash: ByteVector): F[Unit]

  // accounts, storage and codes
  def getMptNode(hash: ByteVector): F[Option[ByteVector]]

  def putMptNode(hash: ByteVector, bytes: ByteVector): F[Unit]

  def getAccount(address: Address, blockNumber: N): F[Option[Account]]

  def getStorage(rootHash: ByteVector, position: N): F[ByteVector]

  def getCode(codeHash: ByteVector): F[Option[ByteVector]]

  def putCode(hash: ByteVector, evmCode: ByteVector): F[Unit]

  def getWorldState(
                     accountStartNonce: UInt256 = UInt256.zero,
                     stateRootHash: Option[ByteVector] = None,
                     noEmptyAccounts: Boolean = true
  ): F[WorldState[F]]

  // helpers
  def getTotalDifficultyByHash(blockHash: ByteVector): F[Option[N]]

  def getTotalDifficultyByNumber(blockNumber: N): F[Option[N]]

  def getTransactionLocation(txHash: ByteVector): F[Option[TransactionLocation]]

  def getBestBlockNumber: F[N]

  def getBestBlockHeader: F[BlockHeader]

  def getBestBlock: F[Block]

  def putBestBlockNumber(number: N): F[Unit]

  def getHashByBlockNumber(number: N): F[Option[ByteVector]]

  def genesisHeader: F[BlockHeader]
}

object History {
  def apply[F[_]](store: KVStore[F])(implicit F: Sync[F], chainId: ChainId, T: Timer[F]): History[F] =
    new HistoryImpl[F](chainId, store, Metrics.nop[F])

  def apply[F[_]](store: KVStore[F], chainId: ChainId)(implicit F: Sync[F], T: Timer[F]): History[F] =
    new HistoryImpl[F](chainId, store, Metrics.nop[F])

  def apply[F[_]](store: KVStore[F], metrics: Metrics[F])(implicit F: Sync[F], chainId: ChainId, T: Timer[F]): History[F] =
    new HistoryImpl[F](chainId, store, metrics)
}
