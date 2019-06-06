package jbok.core.models

import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._
import jbok.codec.rlp.{RlpCodec, RlpEncoded}
import jbok.codec.rlp.implicits._
import jbok.common.math.N
import jbok.crypto._
import scodec.bits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("BlockHeader")
@JSExportAll
@ConfiguredJsonCodec
final case class BlockHeader(
    parentHash: ByteVector,
    beneficiary: ByteVector,
    stateRoot: ByteVector,
    transactionsRoot: ByteVector,
    receiptsRoot: ByteVector,
    logsBloom: ByteVector,
    difficulty: N,
    number: N,
    gasLimit: N,
    gasUsed: N,
    unixTimestamp: Long,
    extra: RlpEncoded
) {
  lazy val bytes: RlpEncoded = this.encoded

  lazy val hash: ByteVector = bytes.bytes.kec256

  def extraAs[A: RlpCodec]: Either[Throwable, A] =
    extra.decoded[A]
}
