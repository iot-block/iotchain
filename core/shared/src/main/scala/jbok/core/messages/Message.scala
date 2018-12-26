package jbok.core.messages

import java.util.UUID

import jbok.codec.rlp.implicits._
import jbok.core.models._
import jbok.core.sync.NodeHash
import jbok.crypto.signature.CryptoSignature
import jbok.network.common.{RequestId, RequestMethod}
import scodec.bits.ByteVector

sealed abstract class Message(val code: Int, val id: String = UUID.randomUUID().toString)

case class Handshake(
    version: Int,
    listenPort: Int,
    sid: ByteVector
) extends Message(0x00)

case class Status(
    chainId: BigInt,
    genesisHash: ByteVector,
    bestNumber: BigInt
) extends Message(0x1000) {
  def isCompatible(other: Status): Boolean =
    chainId == other.chainId && genesisHash == other.genesisHash
}

case class BlockHash(hash: ByteVector, number: BigInt)
case class NewBlockHashes(hashes: List[BlockHash])          extends Message(0x1001)
case class SignedTransactions(txs: List[SignedTransaction]) extends Message(0x1002)

case class GetBlockHeaders(
    block: Either[BigInt, ByteVector],
    maxHeaders: Int,
    skip: Int,
    reverse: Boolean,
    override val id: String = UUID.randomUUID().toString
) extends Message(0x1003)
case class BlockHeaders(headers: List[BlockHeader], override val id: String) extends Message(0x1004)

case class GetBlockBodies(hashes: List[ByteVector], override val id: String = UUID.randomUUID().toString)
    extends Message(0x1005)
case class BlockBodies(bodies: List[BlockBody], override val id: String) extends Message(0x1006)
case class NewBlock(block: Block)                                        extends Message(0x1007)

case class GetNodeData(nodeHashes: List[NodeHash], override val id: String = UUID.randomUUID().toString)
    extends Message(0x100d)
case class NodeData(values: List[ByteVector], override val id: String) extends Message(0x100e)

case class GetReceipts(blockHashes: List[ByteVector], override val id: String = UUID.randomUUID().toString)
    extends Message(0x100f)
case class Receipts(receiptsForBlocks: List[List[Receipt]], override val id: String) extends Message(0x1010)

case class AuthPacket(bytes: ByteVector) extends Message(0x2000)

case class IstanbulMessage(
    msgCode: Int,
    msg: ByteVector,
    address: Address,
    signature: ByteVector,
    committedSig: Option[CryptoSignature]
) extends Message(0x5000)

object IstanbulMessage {
  val msgPreprepareCode = 0
  val msgPrepareCode    = 1
  val msgCommitCode     = 2
  val msgRoundChange    = 3
  val msgAll            = 4
}

object Message {
  implicit val codec = RlpCodec[Message]

  implicit val I: RequestId[Message] = new RequestId[Message] {
    override def id(a: Message): String = a.id
  }

  implicit val M: RequestMethod[Message] = new RequestMethod[Message] {
    override def method(a: Message): Option[String] = Some(a.code.toString)
  }
}
