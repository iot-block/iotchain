package jbok.core.messages

import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import scodec.Attempt
import scodec.bits.ByteVector

trait Message {
  def name = getClass.getSimpleName
}

object Messages {
  val codecMap = Map(
    "Hello" -> RlpCodec[Hello],
    "Status" -> RlpCodec[Status],
    "SignedTransactions" -> RlpCodec[SignedTransactions],
    "GetReceipts" -> RlpCodec[GetReceipts],
    "Receipts" -> RlpCodec[Receipts],
    "GetBlockBodies" -> RlpCodec[GetBlockBodies],
    "BlockBodies" -> RlpCodec[BlockBodies],
    "GetBlockHeaders" -> RlpCodec[GetBlockHeaders],
    "BlockHeaders" -> RlpCodec[BlockHeaders],
    "NewBlock" -> RlpCodec[NewBlock],
    "NewBlockHashes" -> RlpCodec[NewBlockHashes]
  )

  def encode(msg: Message): ByteVector =
    rstring.encode(msg.name).require.bytes ++ (msg.name match {
      case "Hello"              => RlpCodec[Hello].encode(msg.asInstanceOf[Hello])
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

  def main(args: Array[String]): Unit = {
    val status = Status(1, 1, ByteVector.empty, ByteVector.empty)

    val bytes = encode(status)
    val msg = decode(bytes).require
    println(msg)
  }
}
