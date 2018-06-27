package jbok.core.sync

import cats.effect.IO
import jbok.core.Blockchain
import jbok.core.models.BlockHeader
import org.scalatest.WordSpec
import cats.implicits._
import fs2._
import jbok.core.messages.{GetBlockHeaders, MessageFromPeer}
import jbok.p2p.PeerId
import scodec.Codec
import scodec.bits.ByteVector

class SyncServiceSpec extends WordSpec {
  "sync service" should {
    "return Receipts by block hashes" in {
      println(s"bigint ${BigInt.apply("00")}")
    }

    "return BlockBodies by block hashes" in {}

    "return BlockHeaders by block number" in {

      val baseHeader: BlockHeader = BlockHeader(
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        BigInt(0),
        BigInt(0),
        BigInt(0),
        BigInt(0),
        0L,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty
      )

      println(s"header: ${Codec.encode(baseHeader).require.bytes}")

      val firstHeader: BlockHeader = baseHeader.copy(number = 3)
      val secondHeader: BlockHeader = baseHeader.copy(number = 4)

      val blockchain = Blockchain.inMemory[IO].unsafeRunSync()

      {
        blockchain.save(firstHeader) *>
          blockchain.save(secondHeader) *>
          blockchain.save(baseHeader.copy(number = 5)) *>
          blockchain.save(baseHeader.copy(number = 6))
      }.unsafeRunSync()

      val service = SyncService[IO](blockchain)

      val peerId = PeerId("1")
      val send =
        Stream(MessageFromPeer(GetBlockHeaders(Left(3), 2, 0, reverse = false), peerId)).covary[IO]
      val recv = service.handleMessages(send).compile.toList.unsafeRunSync()

      println(recv)
    }

    "return BlockHeaders by block hashes" in {}

    "return MPT Node by hash" in {}
  }
}
