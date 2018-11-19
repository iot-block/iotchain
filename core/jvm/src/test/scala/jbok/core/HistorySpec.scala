package jbok.core

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.models._
import jbok.common.testkit._
import jbok.core.testkit._
import jbok.crypto._
import scodec.bits.ByteVector

class HistorySpec extends JbokSpec {
  implicit val fixture = defaultFixture()

  "History" should {
    // accounts, storages and codes
    "put and get account node" in {
      val history = random[History[IO]]
      forAll { (addr: Address, acc: Account) =>
        val bytes = RlpCodec.encode(acc).require.value.bytes
        history.putMptNode(bytes.kec256, bytes).unsafeRunSync()
        history.getMptNode(bytes.kec256).unsafeRunSync() shouldBe bytes.some
      }
    }

    "put and get storage node" in {
      val history = random[History[IO]]
      forAll { (k: UInt256, v: UInt256) =>
        val bytes = RlpCodec.encode(v).require.value.bytes
        history.putMptNode(bytes.kec256, bytes).unsafeRunSync()
        history.getMptNode(bytes.kec256).unsafeRunSync() shouldBe bytes.some
      }
    }

    "put and get code" in {
      val history = random[History[IO]]
      forAll { (k: ByteVector, v: ByteVector) =>
        history.putCode(k, v).unsafeRunSync()
        history.getCode(k).unsafeRunSync() shouldBe v.some
      }
    }

    // mapping
    "put block header should update number hash mapping" in {
      val history = random[History[IO]]
      history.getHashByBlockNumber(0).unsafeRunSync() shouldBe history.genesisHeader.unsafeRunSync().hash.some
    }

    "put block body should update tx location mapping" in {
      val history = random[History[IO]]
      val txs = random[List[SignedTransaction]](genTxs(1, 1))
      val block= random[Block](genBlock(stxsOpt = txs.some))
      history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
      val location = history.getTransactionLocation(txs.head.hash).unsafeRunSync()
      location shouldBe Some(TransactionLocation(block.header.hash, 0))
    }
  }
}
