package jbok.core.api

import java.util.Date

import cats.effect.Sync
import cats.implicits._
import jbok.core.api.impl.PublicApiImpl
import jbok.core.configs.{BlockChainConfig, MiningConfig}
import jbok.core.keystore.KeyStore
import jbok.core.ledger.{Ledger, OmmersPool}
import jbok.core.mining.BlockGenerator
import jbok.core.models._
import jbok.core.{BlockChain, TxPool}
import jbok.network.JsonRpcAPI
import scodec.bits.ByteVector

case class GetWorkResponse(powHeaderHash: ByteVector, dagSeed: ByteVector, target: ByteVector)
sealed trait BlockParam
object BlockParam {
  case class WithNumber(n: BigInt) extends BlockParam
  case object Latest extends BlockParam
  case object Pending extends BlockParam
  case object Earliest extends BlockParam
}

case class CallTx(
    from: Option[ByteVector],
    to: Option[ByteVector],
    gas: Option[BigInt],
    gasPrice: BigInt,
    value: BigInt,
    data: ByteVector
)

case class SyncingStatus(startingBlock: BigInt, currentBlock: BigInt, highestBlock: BigInt)

trait PublicAPI[F[_]] extends JsonRpcAPI[F] {
  def protocolVersion: R[String]

  def bestBlockNumber: R[BigInt]

  def getBlockTransactionCountByHash(blockHash: ByteVector): R[Option[Int]]

  def getBlockByHash(blockHash: ByteVector): R[Option[Block]]

  def getBlockByNumber(blockNumber: BigInt): R[Option[Block]]

  def getTransactionByHash(txHash: ByteVector): R[Option[SignedTransaction]]

  def getTransactionReceipt(txHash: ByteVector): R[Option[Receipt]]

  def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector, txIndex: Int): R[Option[SignedTransaction]]

  def getUncleByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): R[Option[BlockHeader]]

  def getUncleByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): R[Option[BlockHeader]]

  def submitHashRate(hashRate: BigInt, id: ByteVector): R[Boolean]

  def getGasPrice: R[BigInt]

  def getMining: R[Boolean]

  def getHashRate: R[BigInt]

  def getWork: R[GetWorkResponse]

  def getCoinbase: R[Address]

  def submitWork(nonce: ByteVector, powHeaderHash: ByteVector, mixHash: ByteVector): R[Boolean]

  def syncing: R[Option[SyncingStatus]]

  def sendRawTransaction(data: ByteVector): R[ByteVector]

  def call(callTx: CallTx, blockParam: BlockParam): R[ByteVector]

  def estimateGas(callTx: CallTx, blockParam: BlockParam): R[BigInt]

  def getCode(address: Address, blockParam: BlockParam): R[ByteVector]

  def getUncleCountByBlockNumber(blockParam: BlockParam): R[Int]

  def getUncleCountByBlockHash(blockHash: ByteVector): R[Int]

  def getBlockTransactionCountByNumber(blockParam: BlockParam): R[Int]

  def getTransactionByBlockNumberAndIndexRequest(blockParam: BlockParam, txIndex: Int): R[Option[SignedTransaction]]

  def getBalance(address: Address, blockParam: BlockParam): R[BigInt]

  def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): R[ByteVector]

  def getTransactionCount(address: Address, blockParam: BlockParam): R[BigInt]

  def newFilter(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): R[BigInt]

  def newBlockFilter: R[BigInt]

  def newPendingTransactionFilter: R[BigInt]

  def uninstallFilter(filterId: BigInt): R[Boolean]

  def getFilterChanges(filterId: BigInt): R[FilterChanges]

  def getFilterLogs(filterId: BigInt): R[FilterLogs]

  def getLogs(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): R[LogFilterLogs]

  def getAccountTransactions(address: Address, fromBlock: BigInt, toBlock: BigInt): R[List[Transaction]]
}

object PublicAPI {
  def apply[F[_]: Sync](
      blockChain: BlockChain[F],
      blockChainConfig: BlockChainConfig,
      ommersPool: OmmersPool[F],
      txPool: TxPool[F],
      ledger: Ledger[F],
      blockGenerator: BlockGenerator[F],
      keyStore: KeyStore[F],
      filterManager: FilterManager[F],
      miningConfig: MiningConfig,
      version: Int,
  ): F[PublicAPI[F]] =
    for {
      hashRate <- fs2.async.refOf[F, Map[ByteVector, (BigInt, Date)]](Map.empty)
      lastActive <- fs2.async.refOf[F, Option[Date]](None)
    } yield {
      new PublicApiImpl[F](
        blockChain: BlockChain[F],
        blockChainConfig: BlockChainConfig,
        ommersPool: OmmersPool[F],
        txPool: TxPool[F],
        ledger: Ledger[F],
        blockGenerator: BlockGenerator[F],
        keyStore: KeyStore[F],
        filterManager,
        miningConfig: MiningConfig,
        version: Int,
        hashRate,
        lastActive
      )
    }
}
