package jbok.core.messages

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.network.common.{RequestId, RequestMethod}
import scodec.bits.{BitVector, ByteVector}
import scodec.{Attempt, Codec, DecodeResult, Decoder, Encoder, SizeBound}

trait Message {
  def name = getClass.getSimpleName
}

object Message {
  val codecMap = Map(
    "Handshake"          -> RlpCodec[Handshake],
    "Status"             -> RlpCodec[Status],
    "SignedTransactions" -> RlpCodec[SignedTransactions],
    "GetReceipts"        -> RlpCodec[GetReceipts],
    "Receipts"           -> RlpCodec[Receipts],
    "GetBlockBodies"     -> RlpCodec[GetBlockBodies],
    "BlockBodies"        -> RlpCodec[BlockBodies],
    "GetBlockHeaders"    -> RlpCodec[GetBlockHeaders],
    "BlockHeaders"       -> RlpCodec[BlockHeaders],
    "NewBlock"           -> RlpCodec[NewBlock],
    "NewBlockHashes"     -> RlpCodec[NewBlockHashes]
  )

  def encode(msg: Message): ByteVector =
    rstring.encode(msg.name).require.bytes ++ (msg.name match {
      case "Handshake"          => RlpCodec[Handshake].encode(msg.asInstanceOf[Handshake])
      case "Status"             => RlpCodec[Status].encode(msg.asInstanceOf[Status])
      case "SignedTransactions" => RlpCodec[SignedTransactions].encode(msg.asInstanceOf[SignedTransactions])
      case "GetReceipts"        => RlpCodec[GetReceipts].encode(msg.asInstanceOf[GetReceipts])
      case "Receipts"           => RlpCodec[Receipts].encode(msg.asInstanceOf[Receipts])
      case "GetBlockBodies"     => RlpCodec[GetBlockBodies].encode(msg.asInstanceOf[GetBlockBodies])
      case "BlockBodies"        => RlpCodec[BlockBodies].encode(msg.asInstanceOf[BlockBodies])
      case "GetBlockHeaders"    => RlpCodec[GetBlockHeaders].encode(msg.asInstanceOf[GetBlockHeaders])
      case "BlockHeaders"       => RlpCodec[BlockHeaders].encode(msg.asInstanceOf[BlockHeaders])
      case "NewBlock"           => RlpCodec[NewBlock].encode(msg.asInstanceOf[NewBlock])
      case "NewBlockHashes"     => RlpCodec[NewBlockHashes].encode(msg.asInstanceOf[NewBlockHashes])
    }).require.bytes

  def decode(bytes: ByteVector): Attempt[Message] = rstring.decode(bytes.bits).map { r =>
    val name = r.value
    codecMap(name).decode(r.remainder).require.value.asInstanceOf[Message]
  }

  implicit val encoder: Encoder[Message] = new Encoder[Message] {
    override def encode(value: Message): Attempt[BitVector] =
      Attempt.successful(Message.encode(value).bits)

    override def sizeBound: SizeBound = SizeBound.unknown
  }

  implicit val decoder: Decoder[Message] = new Decoder[Message] {
    override def decode(bits: BitVector): Attempt[DecodeResult[Message]] =
      Message.decode(bits.bytes).map(message => DecodeResult(message, BitVector.empty))
  }

  implicit val codec: Codec[Message] = Codec(encoder, decoder)

  implicit val I: RequestId[Message] = new RequestId[Message] {
    override def id(a: Message): Option[String] = a match {
      case x: SyncMessage => Some(x.id)
      case _              => None
    }
  }

  implicit val M: RequestMethod[Message] = new RequestMethod[Message] {
    override def method(a: Message): Option[String] = Some(a.name)
  }
}
