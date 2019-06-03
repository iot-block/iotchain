package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.common.math.N
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._

@ConfiguredJsonCodec
final case class ChainId(value: N) extends AnyVal

object ChainId {
  implicit val rlpCodec: RlpCodec[ChainId] = RlpCodec.gen

  def apply(i: Int): ChainId = ChainId(N(i))

  def apply(l: Long): ChainId = ChainId(N(l))

  def apply(bi: BigInt): ChainId = ChainId(N(bi))
}
