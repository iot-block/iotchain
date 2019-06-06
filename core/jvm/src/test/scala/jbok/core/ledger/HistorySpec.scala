package jbok.core.ledger

import cats.effect.IO
import cats.implicits._
import jbok.core.ledger.TypedBlock.MinedBlock
import jbok.core.models._
import jbok.core.{CoreSpec, StatefulGen}
import scodec.bits.ByteVector
import jbok.crypto._

class HistorySpec extends CoreSpec {
  "History" should {
    val objects = locator.unsafeRunSync()
    val history = objects.get[History[IO]]

    "load genesis config" in {
      val addresses = genesis.alloc.keysIterator.toList
      val accounts  = addresses.flatMap(addr => history.getAccount(addr, 0).unsafeRunSync())
      accounts.length shouldBe addresses.length
    }

    // storage and codes
    "put and get code" in {
      forAll { code: ByteVector =>
        history.putCode(code).unsafeRunSync()
        history.getCode(code.kec256).unsafeRunSync().getOrElse(ByteVector.empty) shouldBe code
      }
    }

    // mapping
    "put block header should update number hash mapping" in {
      history.getHashByBlockNumber(0).unsafeRunSync() shouldBe history.genesisHeader.unsafeRunSync().hash.some
    }

    "put block body should update tx location mapping" in {
      val txs   = random(StatefulGen.transactions(1, 1, history))
      val block = random(StatefulGen.block(None, txs.some))
      history.putBlockBody(block.header.hash, block.body).unsafeRunSync()
      val location = history.getTransactionLocation(txs.head.hash).unsafeRunSync()
      location shouldBe Some(TransactionLocation(block.header.hash, 0))
    }

    "delBlock should delete all relevant parts" in check { objects =>
      val history = objects.get[History[IO]]
      val mined   = random[MinedBlock](StatefulGen.minedBlock())
      for {
        _ <- history.putBlockAndReceipts(mined.block, mined.receipts)

        res <- history.getBlockHeaderByHash(mined.block.header.hash)
        _ = res shouldBe Some(mined.block.header)

        res <- history.getBlockBodyByHash(mined.block.header.hash)
        _ = res shouldBe Some(mined.block.body)

        res <- history.getBlockByHash(mined.block.header.hash)
        _ = res shouldBe Some(mined.block)

        res <- history.getReceiptsByHash(mined.block.header.hash)
        _ = res shouldBe Some(mined.receipts)

        res <- history.getBestBlockNumber
        _ = res shouldBe mined.block.header.number

        res <- history.getTotalDifficultyByHash(mined.block.header.hash)
        _ = res shouldBe Some(mined.block.header.difficulty)

        res <- history.getHashByBlockNumber(mined.block.header.number)
        _ = res shouldBe Some(mined.block.header.hash)

        // del
        _ <- history.delBlock(mined.block.header.hash)

        res <- history.getBlockHeaderByHash(mined.block.header.hash)
        _ = res shouldBe None

        res <- history.getBlockBodyByHash(mined.block.header.hash)
        _ = res shouldBe None

        res <- history.getBlockByHash(mined.block.header.hash)
        _ = res shouldBe None

        res <- history.getReceiptsByHash(mined.block.header.hash)
        _ = res shouldBe None

        res <- history.getTotalDifficultyByHash(mined.block.header.hash)
        _ = res shouldBe None

        res <- history.getHashByBlockNumber(mined.block.header.number)
        _ = res shouldBe None
      } yield ()
    }
  }
}
