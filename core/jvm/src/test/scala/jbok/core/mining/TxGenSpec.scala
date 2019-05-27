package jbok.core.mining

import cats.effect.IO
import cats.implicits._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.crypto.signature.{ECDSA, Signature}

class TxGenSpec extends CoreSpec {
  "TxGen" should {
    "from history" in check { objects =>
      val history  = objects.get[History[IO]]
      val miner    = objects.get[BlockMiner[IO]]
      val keyPairs = testKeyPair :: Signature[ECDSA].generateKeyPair[IO]().replicateA(5).unsafeRunSync()
      for {
        txGen <- TxGen[IO](keyPairs, history)
        txs   <- txGen.genValidExternalTxN(10)
        mined <- miner.mine(None, Some(txs))
        _ = mined.block.body.transactionList.length shouldBe 10

        txs   <- txGen.genValidExternalTxN(10)
        mined <- miner.mine(None, Some(txs))
        _ = mined.block.body.transactionList.length shouldBe 10
      } yield ()
    }
  }
}
