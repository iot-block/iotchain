package jbok.core.models

import cats.effect.Sync
import io.circe.generic.extras.ConfiguredJsonCodec
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._
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
    difficulty: BigInt,
    number: BigInt,
    gasLimit: BigInt,
    gasUsed: BigInt,
    unixTimestamp: Long,
    extra: ByteVector
) {
  lazy val bytes: ByteVector = this.asBytes

  lazy val hash: ByteVector = bytes.kec256

  def extraAs[F[_], A](implicit F: Sync[F], C: RlpCodec[A]): F[A] =
    F.delay(C.decode(extra.bits).require.value)
}
