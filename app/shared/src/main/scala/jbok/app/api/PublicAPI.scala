package jbok.app.api

import java.util.Date

import cats.effect.IO
import jbok.app.api.impl.PublicApiImpl
import jbok.core.Configs.{BlockChainConfig, MiningConfig}
import jbok.core.History
import jbok.core.keystore.KeyStore
import jbok.core.mining.BlockMiner
import jbok.core.models._
import jbok.network.rpc.RpcAPI
import scodec.bits.ByteVector

case class GetWorkResponse(powHeaderHash: ByteVector, dagSeed: ByteVector, target: ByteVector)
sealed trait BlockParam
object BlockParam {
  case class WithNumber(n: BigInt) extends BlockParam
  case object Latest               extends BlockParam
  case object Pending              extends BlockParam
  case object Earliest             extends BlockParam
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

trait PublicAPI extends RpcAPI {
  def protocolVersion: Response[String]

  def bestBlockNumber: Response[BigInt]

  def getBlockTransactionCountByHash(blockHash: ByteVector): Response[Option[Int]]

  def getBlockByHash(blockHash: ByteVector): Response[Option[Block]]

  def getBlockByNumber(blockNumber: BigInt): Response[Option[Block]]

  def getTransactionByHash(txHash: ByteVector): Response[Option[SignedTransaction]]

  def getTransactionReceipt(txHash: ByteVector): Response[Option[Receipt]]

  def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector, txIndex: Int): Response[Option[SignedTransaction]]

  def getUncleByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): Response[Option[BlockHeader]]

  def getUncleByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): Response[Option[BlockHeader]]

  def submitHashRate(hashRate: BigInt, id: ByteVector): Response[Boolean]

  def getGasPrice: Response[BigInt]

  def getMining: Response[Boolean]

  def getHashRate: Response[BigInt]

  def getWork: Response[GetWorkResponse]

  def getCoinbase: Response[Address]

  def submitWork(nonce: ByteVector, powHeaderHash: ByteVector, mixHash: ByteVector): Response[Boolean]

  def syncing: Response[Option[SyncingStatus]]

  def sendRawTransaction(data: ByteVector): Response[ByteVector]

  def call(callTx: CallTx, blockParam: BlockParam): Response[ByteVector]

  def estimateGas(callTx: CallTx, blockParam: BlockParam): Response[BigInt]

  def getCode(address: Address, blockParam: BlockParam): Response[ByteVector]

  def getUncleCountByBlockNumber(blockParam: BlockParam): Response[Int]

  def getUncleCountByBlockHash(blockHash: ByteVector): Response[Int]

  def getBlockTransactionCountByNumber(blockParam: BlockParam): Response[Int]

  def getTransactionByBlockNumberAndIndexRequest(blockParam: BlockParam,
                                                 txIndex: Int): Response[Option[SignedTransaction]]

  def getBalance(address: Address, blockParam: BlockParam): Response[BigInt]

  def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): Response[ByteVector]

  def getTransactionCount(address: Address, blockParam: BlockParam): Response[BigInt]

  def newFilter(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): Response[BigInt]

  def newBlockFilter: Response[BigInt]

  def newPendingTransactionFilter: Response[BigInt]

  def uninstallFilter(filterId: BigInt): Response[Boolean]

  def getFilterChanges(filterId: BigInt): Response[FilterChanges]

  def getFilterLogs(filterId: BigInt): Response[FilterLogs]

  def getLogs(
      fromBlock: Option[BlockParam],
      toBlock: Option[BlockParam],
      address: Option[Address],
      topics: List[List[ByteVector]]
  ): Response[LogFilterLogs]

  def getAccountTransactions(address: Address, fromBlock: BigInt, toBlock: BigInt): Response[List[Transaction]]
}

object PublicAPI {
  def apply(
      blockChain: History[IO],
      blockChainConfig: BlockChainConfig,
      miningConfig: MiningConfig,
      miner: BlockMiner[IO],
      keyStore: KeyStore[IO],
      filterManager: FilterManager[IO],
      version: Int,
  ): IO[PublicAPI] =
    for {
      hashRate   <- fs2.async.refOf[IO, Map[ByteVector, (BigInt, Date)]](Map.empty)
      lastActive <- fs2.async.refOf[IO, Option[Date]](None)
    } yield {
      new PublicApiImpl(
        blockChainConfig,
        miningConfig: MiningConfig,
        miner,
        keyStore,
        filterManager,
        version: Int,
        hashRate,
        lastActive
      )
    }
}
