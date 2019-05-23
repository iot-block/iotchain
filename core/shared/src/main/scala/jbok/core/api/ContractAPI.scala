package jbok.core.api

import io.circe.generic.JsonCodec
import jbok.core.models.Address
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.network.rpc.PathName

import scala.scalajs.js.annotation.JSExportAll

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

@PathName("contract")
trait ContractAPI[F[_]] {
//  def getABI(address: Address): F[Option[ContractDef]]
//
//  def getSourceCode(address: Address): F[Option[String]]

  def call(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getEstimatedGas(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[BigInt]

  def getGasPrice: F[BigInt]
}
