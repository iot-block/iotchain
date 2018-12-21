package jbok.core.models

import io.circe._
import jbok.codec.json.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.consensus.istanbul.Istanbul
import jbok.crypto._
import scodec.bits._
import shapeless._
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
    extraData: ByteVector, // consensus field
    mixHash: ByteVector, // B32 consensus field
    nonce: ByteVector // B8 consensus field
) {
  lazy val hash: ByteVector =
    if (mixHash == Istanbul.mixDigest) {
      val newHeader = Istanbul.filteredHeader(this, true)
      RlpCodec.encode(newHeader).require.bytes.kec256
    } else {
      RlpCodec.encode(this).require.bytes.kec256
    }

  lazy val hashWithoutNonce: ByteVector = BlockHeader.encodeWithoutNonce(this).kec256

  lazy val tag: String = s"BlockHeader(${number})#${hash.toHex.take(7)}"
}

object BlockHeader {
  implicit val headerJsonEncoder: Encoder[BlockHeader] = deriveEncoder[BlockHeader]

  implicit val headerJsonDecoder: Decoder[BlockHeader] = deriveDecoder[BlockHeader]

  def encodeWithoutNonce(header: BlockHeader): ByteVector = {
    val hlist = Generic[BlockHeader].to(header).take(13)
    RlpCodec.encode(hlist).require.bytes
  }
}
