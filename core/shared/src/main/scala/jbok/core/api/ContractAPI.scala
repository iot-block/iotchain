package jbok.core.api

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.core.models.Address
import scodec.bits.ByteVector
import jbok.codec.json.implicits._
import jbok.common.math.N
import jbok.network.rpc.PathName

import scala.scalajs.js.annotation.JSExportAll

@JSExportAll
@ConfiguredJsonCodec
final case class CallTx(
    from: Option[Address],
    to: Option[Address],
    gas: Option[N],
    gasPrice: N,
    value: N,
    data: ByteVector
)

@PathName("contract")
trait ContractAPI[F[_]] {
//  def getABI(address: Address): F[Option[ContractDef]]
//
//  def getSourceCode(address: Address): F[Option[String]]

  def call(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getEstimatedGas(callTx: CallTx, tag: BlockTag = BlockTag.latest): F[N]

  def getGasPrice: F[N]
}
