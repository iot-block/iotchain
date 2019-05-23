package jbok.core.messages

import io.circe.generic.JsonCodec
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import jbok.codec.json.implicits._

@JsonCodec
final case class Status(chainId: BigInt, genesisHash: ByteVector, bestNumber: BigInt, td: BigInt, service: String) {
  def isCompatible(other: Status): Boolean =
    chainId == other.chainId && genesisHash == other.genesisHash
}

object Status {
  val name = "Status"

  implicit val rlpCodec: RlpCodec[Status] = RlpCodec.gen[Status]
}

