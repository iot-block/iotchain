package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.common.math.N

@ConfiguredJsonCodec
final case class ChainId(value: N) extends AnyVal

object ChainId {
  implicit val rlpCodec: RlpCodec[ChainId] = RlpCodec.gen[ChainId]

  def apply(i: Int): ChainId = ChainId(N(i))

  def apply(l: Long): ChainId = ChainId(N(l))

  def apply(bi: BigInt): ChainId = ChainId(N(bi))
}
