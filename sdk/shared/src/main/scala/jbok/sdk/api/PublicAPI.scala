package jbok.sdk.api

import io.circe.generic.JsonCodec
import jbok.core.models._
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.solidity.ABIDescription.ContractDescription

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JsonCodec
sealed trait BlockParam

object BlockParam {
  @JSExportTopLevel("BlockParam.WithNumber")
  @JSExportAll
  final case class WithNumber(n: BigInt) extends BlockParam
  @JSExportTopLevel("BlockParam.Latest")
  final case object Latest extends BlockParam
  @JSExportTopLevel("BlockParam.Earliest")
  final case object Earliest extends BlockParam
}

@JSExportAll
@JsonCodec
final case class CallTx(
    from: Option[Address],
    to: Option[Address],
    gas: Option[BigInt],
    gasPrice: BigInt,
    value: BigInt,
    data: ByteVector
)

@JsonCodec
final case class ParseResult(
    contract: Option[ContractDescription],
    errors: Option[List[String]]
)

trait PublicAPI[F[_]] {
  def bestBlockNumber: F[BigInt]

  // tx count
  def getTransactionCount(address: Address, blockParam: BlockParam): F[BigInt]

  def getBlockTransactionCountByHash(blockHash: ByteVector): F[Option[Int]]

  def getBlockTransactionCountByNumber(blockParam: BlockParam): F[Int]

  // block
  def getBlockByHash(blockHash: ByteVector): F[Option[Block]]

  def getBlockByNumber(blockNumber: BigInt): F[Option[Block]]

  // tx
  def getTransactionByHashFromHistory(txHash: ByteVector): F[Option[SignedTransaction]]

  def getTransactionByHashFromTxPool(txHash: ByteVector): F[Option[SignedTransaction]]

  def getTransactionsInTxPool(address: Address): F[List[SignedTransaction]]

  def getTransactionReceipt(txHash: ByteVector): F[Option[Receipt]]

  def getTransactionByBlockHashAndIndexRequest(blockHash: ByteVector, txIndex: Int): F[Option[SignedTransaction]]

  def getTransactionByBlockNumberAndIndexRequest(blockParam: BlockParam, txIndex: Int): F[Option[SignedTransaction]]

  // ommer
  def getOmmerByBlockHashAndIndex(blockHash: ByteVector, ommerIndex: Int): F[Option[BlockHeader]]

  def getOmmerByBlockNumberAndIndex(blockParam: BlockParam, ommerIndex: Int): F[Option[BlockHeader]]

  def getOmmerCountByBlockNumber(blockParam: BlockParam): F[Int]

  def getOmmerCountByBlockHash(blockHash: ByteVector): F[Int]

  def isMining: F[Boolean]

  // send tx
  def sendSignedTransaction(stx: SignedTransaction): F[ByteVector]

  def sendRawTransaction(data: ByteVector): F[ByteVector]

  def call(callTx: CallTx, blockParam: BlockParam): F[ByteVector]

  def getEstimatedGas(callTx: CallTx, blockParam: BlockParam): F[BigInt]

  def getGasPrice: F[BigInt]

  def getEstimatedNonce(address: Address): F[BigInt]

  // account
  def getCode(address: Address, blockParam: BlockParam): F[ByteVector]

  def getAccount(address: Address, blockParam: BlockParam): F[Account]

  def getBalance(address: Address, blockParam: BlockParam): F[BigInt]

  def getStorageAt(address: Address, position: BigInt, blockParam: BlockParam): F[ByteVector]

  // contract
  def parseContractCode(code: String): F[ParseResult]

  def callContractTransaction(code: String,
                              methods: String,
                              params: String,
                              from: Address,
                              contractAddress: Address,
                              blockParam: BlockParam): F[String]
}
