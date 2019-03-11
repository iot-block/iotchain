package jbok.core.models

import cats.effect.Sync
import io.circe._
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._
import jbok.crypto._
import scodec.bits._

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("BlockHeader")
@JSExportAll
final case class BlockHeader(
    parentHash: ByteVector, // B32 pre
    ommersHash: ByteVector, // B32 body
    beneficiary: ByteVector, // B20 pre
    stateRoot: ByteVector, // B32 exec
    transactionsRoot: ByteVector, // B32 body
    receiptsRoot: ByteVector, // B32 exec
    logsBloom: ByteVector, // B256 post exec
    difficulty: BigInt, // consensus
    number: BigInt, // pre
    gasLimit: BigInt, // consensus field
    gasUsed: BigInt, // post
    unixTimestamp: Long, // pre
    extra: ByteVector
) {
  lazy val bytes: ByteVector = this.asValidBytes

  lazy val hash: ByteVector = bytes.kec256

  lazy val tag: String = s"BlockHeader(${number})#${hash.toHex.take(7)}"

  def extraAs[F[_], A](implicit F: Sync[F], C: RlpCodec[A]): F[A] =
    F.delay(C.decode(extra.bits).require.value)
}

object BlockHeader {
  implicit val headerJsonEncoder: Encoder[BlockHeader] = deriveEncoder[BlockHeader]

  implicit val headerJsonDecoder: Decoder[BlockHeader] = deriveDecoder[BlockHeader]
}
