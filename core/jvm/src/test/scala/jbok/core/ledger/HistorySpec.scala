package jbok.core.ledger

import cats.effect.IO
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.models._
import jbok.core.testkit._
import jbok.crypto._
import scodec.bits.ByteVector

class HistorySpec extends CoreSpec {
  "History" should {
    val objects = locator.unsafeRunSync()
    val history = objects.get[History[IO]]

    "load genesis config" in {
      val addresses = genesis.alloc.keysIterator.toList
      val accounts  = addresses.flatMap(addr => history.getAccount(addr, 0).unsafeRunSync())
      accounts.length shouldBe addresses.length
    }

    // accounts, storages and codes
    "put and get account node" in {
      forAll { (addr: Address, acc: Account) =>
        val bytes = acc.asBytes
        history.putMptNode(bytes.kec256, bytes).unsafeRunSync()
        history.getMptNode(bytes.kec256).unsafeRunSync() shouldBe bytes.some
      }
    }

    "put and get storage node" in {
      forAll { (k: UInt256, v: UInt256) =>
        val bytes = v.asBytes
        history.putMptNode(bytes.kec256, bytes).unsafeRunSync()
        history.getMptNode(bytes.kec256).unsafeRunSync() shouldBe bytes.some
      }
    }

    "put and get code" in {
      forAll { (k: ByteVector, v: ByteVector) =>
        history.putCode(k, v).unsafeRunSync()
        history.getCode(k).unsafeRunSync() shouldBe v.some
      }
    }

    // mapping
    "put block header should update number hash mapping" in {
      history.getHashByBlockNumber(0).unsafeRunSync() shouldBe history.genesisHeader.unsafeRunSync().hash.some
    }

    "put block body should update tx location mapping" in {
      val txs   = random[List[SignedTransaction]](genTxs(1, 1, history))
      val block = random[Block](genBlock(stxsOpt = txs.some))
      history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
      val location = history.getTransactionLocation(txs.head.hash).unsafeRunSync()
      location shouldBe Some(TransactionLocation(block.header.hash, 0))
    }
  }
}
