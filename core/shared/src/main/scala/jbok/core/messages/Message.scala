package jbok.core.messages

import java.util.UUID

import jbok.core.models._
import jbok.core.sync.NodeHash
import jbok.network.common.{RequestId, RequestMethod}
import scodec.Codec
import scodec.bits.ByteVector
import scodec.codecs.{discriminated, uint16}
import jbok.codec.rlp.implicits._

sealed abstract class Message(val code: Int, val id: String = UUID.randomUUID().toString)

case class Handshake(
    version: Int,
    listenPort: Int,
    sid: ByteVector
) extends Message(0x00)

case class Status(
    chainId: Int,
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

object Message {
  implicit val codec: Codec[Message] = {
    discriminated[Message]
      .by(uint16)
      .subcaseO(0x0000) {
        case x: Handshake => Some(x)
        case _            => None
      }(Codec[Handshake])
      .subcaseO(0x1000) {
        case x: Status => Some(x)
        case _         => None
      }(Codec[Status])
      .subcaseO(0x1001) {
        case x: NewBlockHashes => Some(x)
        case _                 => None
      }(Codec[NewBlockHashes])
      .subcaseO(0x1002) {
        case x: SignedTransactions => Some(x)
        case _                     => None
      }(Codec[SignedTransactions])
      .subcaseO(0x1003) {
        case x: GetBlockHeaders => Some(x)
        case _                  => None
      }(Codec[GetBlockHeaders])
      .subcaseO(0x1004) {
        case x: BlockHeaders => Some(x)
        case _               => None
      }(Codec[BlockHeaders])
      .subcaseO(0x1005) {
        case x: GetBlockBodies => Some(x)
        case _                 => None
      }(Codec[GetBlockBodies])
      .subcaseO(0x1006) {
        case x: BlockBodies => Some(x)
        case _              => None
      }(Codec[BlockBodies])
      .subcaseO(0x1007) {
        case x: NewBlock => Some(x)
        case _           => None
      }(Codec[NewBlock])
      .subcaseO(0x100d) {
        case x: GetNodeData => Some(x)
        case _              => None
      }(Codec[GetNodeData])
      .subcaseO(0x100e) {
        case x: NodeData => Some(x)
        case _           => None
      }(Codec[NodeData])
      .subcaseO(0x100f) {
        case x: GetReceipts => Some(x)
        case _              => None
      }(Codec[GetReceipts])
      .subcaseO(0x1010) {
        case x: Receipts => Some(x)
        case _           => None
      }(Codec[Receipts])
      .subcaseO(0x2000) {
        case x: AuthPacket => Some(x)
        case _             => None
      }(Codec[AuthPacket])
  }

  implicit val I: RequestId[Message] = new RequestId[Message] {
    override def id(a: Message): String = a.id
  }

  implicit val M: RequestMethod[Message] = new RequestMethod[Message] {
    override def method(a: Message): Option[String] = Some(a.code.toString)
  }
}
