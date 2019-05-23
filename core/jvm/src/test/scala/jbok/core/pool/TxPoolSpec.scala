package jbok.core.pool

import cats.effect.IO
import cats.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.messages.SignedTransactions
import jbok.core.models.SignedTransaction
import jbok.core.testkit._
import jbok.crypto.signature.KeyPair
import jbok.crypto.testkit._
import monocle.macros.syntax.lens._
import scala.concurrent.duration._

class TxPoolSpec extends CoreSpec {
  val keyPair = Some(testMiner.keyPair)
  "TxPool" should {
    "store pending transactions" in check { objects =>
      val txPool = objects.get[TxPool[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 10, keyPair))
      for {
        _   <- txPool.addTransactions(SignedTransactions(txs))
        res <- txPool.getPendingTransactions.map(_.keys)
        _ = res should contain theSameElementsAs txs
      } yield ()
    }

    "ignore known transactions" in check { objects =>
      val txPool = objects.get[TxPool[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 10, keyPair))
      val stxs   = SignedTransactions(txs)
      for {
        _   <- txPool.addTransactions(stxs)
        _   <- txPool.addTransactions(stxs)
        res <- txPool.getPendingTransactions.map(_.keys)
        _ = res should contain theSameElementsAs txs
      } yield ()
    }
//
//    "broadcast received pending transactions to other peers" in check { objects =>
//      val txPool = objects.get[TxPool[IO]]
//      val txs    = random[List[SignedTransaction]](genTxs(1, 10))
////      val peerSet = genPeerSet(1, 10).sample.get
//      val output = txPool.addTransactions(SignedTransactions(txs)).unsafeRunSync()
//      ???
////      output.length shouldBe peerSet.connected.map(_.length).unsafeRunSync()
//    }

    "override transactions with the same sender and nonce" in check { objects =>
      val txPool = objects.get[TxPool[IO]]
      val tx1    = genTx.sample.get
      val tx2    = genTx.sample.get
      val tx3    = genTx.sample.get

      val kp1 = random[KeyPair]
      val kp2 = random[KeyPair]

      val first  = SignedTransaction.sign[IO](tx1, kp1).unsafeRunSync()
      val second = SignedTransaction.sign[IO](tx2.copy(nonce = tx1.nonce), kp1).unsafeRunSync()
      val other  = SignedTransaction.sign[IO](tx3, kp2).unsafeRunSync()

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
      val stx    = random[List[SignedTransaction]](genTxs(1, 1, keyPair)).head
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
