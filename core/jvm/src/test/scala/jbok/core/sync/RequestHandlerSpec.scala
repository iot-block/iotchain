package jbok.core.sync

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.messages._
import jbok.core.models.{Block, BlockHeader, Receipt}
import jbok.core.peer.{Peer, PeerSet, Request}
import jbok.common.testkit._
import jbok.crypto.testkit._
import jbok.core.testkit._
import jbok.crypto.authds.mpt.MptNode
import scodec.bits._

class RequestHandlerSpec extends JbokSpec {
  "RequestHandler" should {
    implicit val fixture = defaultFixture()

    "return receipts by block hashes" in {
      val handler = random[RequestHandler[IO]]
      forAll { (peer: Peer[IO], ps: PeerSet[IO], receipts: List[Receipt], hash: ByteVector) =>
        handler.history.putReceipts(hash, receipts).unsafeRunSync()
        val msg       = GetReceipts(hash :: Nil)
        val req       = Request(peer, ps, msg)
        val Some(res) = handler.service.run(req).value.unsafeRunSync()
        res shouldBe peer -> Receipts(receipts :: Nil, msg.id) :: Nil
      }
    }

    "return BlockBodies by block hashes" in {
      val handler = random[RequestHandler[IO]]
      forAll { (peer: Peer[IO], ps: PeerSet[IO], block: Block) =>
        handler.history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
        val msg       = GetBlockBodies(block.header.hash :: Nil)
        val request   = Request(peer, ps, msg)
        val Some(res) = handler.service.run(request).value.unsafeRunSync()
        res shouldBe peer -> BlockBodies(block.body :: Nil, msg.id) :: Nil
      }
    }

    "return BlockHeaders by block number/hash" in {
      val handler = random[RequestHandler[IO]]

      val baseHeader: BlockHeader   = BlockHeader.empty
      val firstHeader: BlockHeader  = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      handler.history.putBlockHeader(firstHeader).unsafeRunSync()
      handler.history.putBlockHeader(secondHeader).unsafeRunSync()
      handler.history.putBlockHeader(baseHeader.copy(number = 5)).unsafeRunSync()
      handler.history.putBlockHeader(baseHeader.copy(number = 6)).unsafeRunSync()
      forAll { (peer: Peer[IO], ps: PeerSet[IO]) =>
        val msg1      = GetBlockHeaders(Left(3), 2, 0, reverse = false)
        val request   = Request(peer, ps, msg1)
        val Some(res) = handler.service.run(request).value.unsafeRunSync()
        res shouldBe peer -> BlockHeaders(firstHeader :: secondHeader :: Nil, msg1.id) :: Nil
        val msg2       = GetBlockHeaders(Right(firstHeader.hash), 2, 0, reverse = false)
        val request2   = Request(peer, ps, msg2)
        val Some(res2) = handler.service.run(request2).value.unsafeRunSync()
        res2 shouldBe peer -> BlockHeaders(firstHeader :: secondHeader :: Nil, msg2.id) :: Nil
      }
    }

    "return NodeData by node hashes" in {
      val handler = random[RequestHandler[IO]]
      forAll { (peer: Peer[IO], ps: PeerSet[IO], node: MptNode) =>
        val msg = GetNodeData(NodeHash.StateMptNodeHash(node.hash) :: Nil)
        handler.history.putMptNode(node.hash, node.bytes).unsafeRunSync()
        val request   = Request(peer, ps, msg)
        val Some(res) = handler.service.run(request).value.unsafeRunSync()
        res shouldBe peer -> NodeData(node.bytes :: Nil, msg.id) :: Nil
      }
    }
  }
}
