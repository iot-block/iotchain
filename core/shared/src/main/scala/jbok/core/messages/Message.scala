package jbok.core.messages

import jbok.codec.rlp.rstring
import scodec.bits.ByteVector
import scodec.{Attempt, Codec, Decoder, Encoder}

trait Message {
  def name = getClass.getSimpleName
}

object Messages {
  val encoders: Map[String, Encoder[Message]] = Map(
    "Hello" -> Encoder[Hello].contramap[Message](_.asInstanceOf[Hello]),
    "Status" -> Encoder[Status].contramap[Message](_.asInstanceOf[Status]),
    "SignedTransactions" -> Encoder[SignedTransactions].contramap[Message](_.asInstanceOf[SignedTransactions]),
    "GetReceipts" -> Encoder[GetReceipts].contramap[Message](_.asInstanceOf[GetReceipts]),
    "Receipts" -> Encoder[Receipts].contramap[Message](_.asInstanceOf[Receipts]),
    "GetBlockBodies" -> Encoder[GetBlockBodies].contramap[Message](_.asInstanceOf[GetBlockBodies]),
    "BlockBodies" -> Encoder[BlockBodies].contramap[Message](_.asInstanceOf[BlockBodies]),
    "GetBlockHeaders" -> Encoder[GetBlockHeaders].contramap[Message](_.asInstanceOf[GetBlockHeaders]),
    "BlockHeaders" -> Encoder[BlockHeaders].contramap[Message](_.asInstanceOf[BlockHeaders]),
    "NewBlock" -> Encoder[NewBlock].contramap[Message](_.asInstanceOf[NewBlock]),
    "NewBlockHashes" -> Encoder[NewBlockHashes].contramap[Message](_.asInstanceOf[NewBlockHashes])
  )

  val decoders: Map[String, Decoder[Message]] = Map(
    "Hello" -> Decoder[Hello],
    "Status" -> Decoder[Status],
    "SignedTransactions" -> Decoder[SignedTransactions],
    "GetReceipts" -> Decoder[GetReceipts],
    "Receipts" -> Decoder[Receipts],
    "GetBlockBodies" -> Decoder[GetBlockBodies],
    "BlockBodies" -> Decoder[BlockBodies],
    "GetBlockHeaders" -> Decoder[GetBlockHeaders],
    "BlockHeaders" -> Decoder[BlockHeaders],
    "NewBlock" -> Decoder[NewBlock],
    "NewBlockHashes" -> Decoder[NewBlockHashes]
  )

  def encode(msg: Message): ByteVector =
    rstring.encode(msg.name).require.bytes ++ encoders(msg.name).encode(msg).require.bytes

  def decode(bytes: ByteVector): Attempt[Message] = rstring.decode(bytes.bits).map { r =>
    val name = r.value
    decoders(name).decode(r.remainder).require.value.asInstanceOf[Message]
  }

  def main(args: Array[String]): Unit = {
    val status = Status(1, 1, ByteVector.empty, ByteVector.empty)

    val bytes = encode(status)
    val msg = decode(bytes).require
    println(msg)
  }
}

//case class MessageSpec[A <: Message](
//    name: String,
//    codec: Codec[A]
//)
//
//object MessageSpec {
//  def apply[A <: Message](implicit codec: Codec[A]): MessageSpec[A] = {
//    val name = classOf[A].getSimpleName
//    MessageSpec[A](name, codec)
//  }
//}
