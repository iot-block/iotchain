package jbok.app.api

import cats.effect.IO
import io.circe.generic.JsonCodec
import jbok.core.models._
import scodec.bits.ByteVector
import jbok.codec.json.implicits._

@JsonCodec
case class GetWorkResponse(powHeaderHash: ByteVector, dagSeed: ByteVector, target: ByteVector)

@JsonCodec
sealed trait BlockParam
object BlockParam {
  case class WithNumber(n: BigInt) extends BlockParam
  case object Latest               extends BlockParam
  case object Earliest             extends BlockParam
}

@JsonCodec
case class CallTx(
    from: Option[Address],
    to: Option[Address],
    gas: Option[BigInt],
    gasPrice: BigInt,
    value: BigInt,
    data: ByteVector
)

@JsonCodec
case class SyncingStatus(startingBlock: BigInt, currentBlock: BigInt, highestBlock: BigInt)

trait PublicAPI {
  def protocolVersion: IO[String]

  def bestBlockNumber: IO[BigInt]

  def getBlockTransactionCountByHash(blockHash: ByteVector): IO[Option[Int]]

  def getBlockByHash(blockHash: ByteVector): IO[Option[Block]]

  def getBlockByNumber(blockNumber: BigInt): IO[Option[Block]]

  def getTransactionByHash(txHash: ByteVector): IO[Option[SignedTransaction]]

  def getTransactionReceipt(txHash: ByteVector): IO[Option[Receipt]]

  def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector, txIndex: Int): IO[Option[SignedTransaction]]

  def getUncleByBlockHashAndIndex(blockHash: ByteVector, uncleIndex: Int): IO[Option[BlockHeader]]

  def getUncleByBlockNumberAndIndex(blockParam: BlockParam, uncleIndex: Int): IO[Option[BlockHeader]]

  def submitHashRate(hashRate: BigInt, id: ByteVector): IO[Boolean]

  def getGasPrice: IO[BigInt]

  def isMining: IO[Boolean]

  def getCoinbase: IO[Address]

  def syncing: IO[Option[SyncingStatus]]

  def sendRawTransaction(data: ByteVector): IO[ByteVector]

  def call(callTx: CallTx, blockParam: BlockParam): IO[ByteVector]

  def estimateGas(callTx: CallTx, blockParam: BlockParam): IO[BigInt]

  def getCode(address: Address, blockParam: BlockParam): IO[ByteVector]

  def getUncleCountByBlockNumber(blockParam: BlockParam): IO[Int]

  def getUncleCountByBlockHash(blockHash: ByteVector): IO[Int]

  def getBlockTransactionCountByNumber(blockParam: BlockParam): IO[Int]

  def getTransactionByBlockNumberAndIndexRequest(blockParam: BlockParam, txIndex: Int): IO[Option[SignedTransaction]]

  def getAccount(address: Address, blockParam: BlockParam): IO[Account]

  def getBalance(address: Address, blockParam: BlockParam): IO[BigInt]

  def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): IO[ByteVector]

  def getTransactionCount(address: Address, blockParam: BlockParam): IO[BigInt]

  def getAccountTransactions(address: Address, fromBlock: BigInt, toBlock: BigInt): IO[List[SignedTransaction]]
}
