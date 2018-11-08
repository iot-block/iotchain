package jbok.core

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.common.testkit._
import jbok.core.models._
import jbok.crypto._
import jbok.persistent.KeyValueDB
import jbok.core.testkit._
import scodec.bits.ByteVector

trait HistoryFixture {
  val db      = KeyValueDB.inmem[IO].unsafeRunSync()
  val history = History[IO](db).unsafeRunSync()
  history.init().unsafeRunSync()
}

class HistorySpec extends JbokSpec {

  "History" should {
    // accounts, storages and codes
    "put and get account node" in new HistoryFixture {
      forAll { (addr: Address, acc: Account) =>
        val bytes = RlpCodec.encode(acc).require.value.bytes
        history.putAccountNode(bytes.kec256, bytes).unsafeRunSync()
        history.getAccountNode(bytes.kec256).unsafeRunSync() shouldBe bytes.some
      }
    }

    "put and get storage node" in new HistoryFixture {
      forAll { (k: UInt256, v: UInt256) =>
        val bytes = RlpCodec.encode(v).require.value.bytes
        history.putStorageNode(bytes.kec256, bytes).unsafeRunSync()
        history.getStorageNode(bytes.kec256).unsafeRunSync() shouldBe bytes.some
      }
    }

    "put and get code" in new HistoryFixture {
      forAll { (k: ByteVector, v: ByteVector) =>
        history.putCode(k, v).unsafeRunSync()
        history.getCode(k).unsafeRunSync() shouldBe v.some
      }
    }

    // mapping
    "put block header should update number hash mapping" in new HistoryFixture {
      history.getHashByBlockNumber(0).unsafeRunSync() shouldBe history.genesisHeader.unsafeRunSync().hash.some
    }

    "put block body should update tx location mapping" in new HistoryFixture {}
  }
}
