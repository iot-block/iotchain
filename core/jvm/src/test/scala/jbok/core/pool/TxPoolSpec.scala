package jbok.core.pool

import cats.effect.IO
import cats.implicits._
import jbok.core.{CoreSpec, StatefulGen}
import jbok.core.ledger.History
import jbok.core.messages.SignedTransactions
import jbok.core.models.{SignedTransaction, Transaction}
import jbok.crypto.signature.KeyPair
import jbok.crypto.testkit._
import monocle.macros.syntax.lens._

import scala.concurrent.duration._

class TxPoolSpec extends CoreSpec {
  "TxPool" should {
    "store pending transactions" in check { objects =>
      val txPool = objects.get[TxPool[IO]]
      val history = objects.get[History[IO]]
      val txs    = random(StatefulGen.transactions(1, 10, history))
      for {
        _   <- txPool.addTransactions(SignedTransactions(txs))
        res <- txPool.getPendingTransactions.map(_.keys)
        _ = res should contain theSameElementsAs txs
      } yield ()
    }

    "ignore known transactions" in check { objects =>
      val txPool = objects.get[TxPool[IO]]
      val history = objects.get[History[IO]]
      val txs    = random(StatefulGen.transactions(1, 10, history))
      val stxs   = SignedTransactions(txs)
      for {
        _   <- txPool.addTransactions(stxs)
        _   <- txPool.addTransactions(stxs)
        res <- txPool.getPendingTransactions.map(_.keys)
        _ = res should contain theSameElementsAs txs
      } yield ()
    }

    "override transactions with the same sender and nonce" in check { objects =>
      val txPool = objects.get[TxPool[IO]]
      val tx1    = random[Transaction]
      val tx2    = random[Transaction]
      val tx3    = random[Transaction]

      val kp1 = random[KeyPair]
      val kp2 = random[KeyPair]

      val first  = SignedTransaction.sign[IO](tx1, kp1, chainId).unsafeRunSync()
      val second = SignedTransaction.sign[IO](tx2.copy(nonce = tx1.nonce), kp1, chainId).unsafeRunSync()
      val other  = SignedTransaction.sign[IO](tx3, kp2, chainId).unsafeRunSync()

      for {
        _ <- txPool.addOrUpdateTransaction(first)
        _ <- txPool.addOrUpdateTransaction(second)
        _ <- txPool.addOrUpdateTransaction(other)
        x <- txPool.getPendingTransactions
        _ = x.keys should contain theSameElementsAs List(other, second)
      } yield ()
    }

    "remove transaction on timeout" in check(config.lens(_.txPool.transactionTimeout).set(100.millis)) { objects =>
      val txPool = objects.get[TxPool[IO]]
      val history = objects.get[History[IO]]
      val stx    = random(StatefulGen.transactions(1, 1, history)).head
      for {
        _  <- txPool.addTransactions(SignedTransactions(stx :: Nil))
        p1 <- txPool.getPendingTransactions
        _ = p1.size shouldBe 1
        p2 <- timer.sleep(2.seconds) >> txPool.getPendingTransactions
        _ = p2.size shouldBe 0
      } yield ()
    }
  }
}
