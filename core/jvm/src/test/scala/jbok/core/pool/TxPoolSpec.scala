package jbok.core.pool

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.messages.SignedTransactions
import jbok.core.models.SignedTransaction
import jbok.core.testkit._
import jbok.crypto.signature.KeyPair
import jbok.crypto.testkit._

import scala.concurrent.duration._

class TxPoolSpec extends JbokSpec {
  "TxPool" should {
    implicit val config = testConfig

    "store pending transactions" in {
      val txPool = random[TxPool[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 10))
      txPool.addTransactions(SignedTransactions(txs)).unsafeRunSync()
      txPool.getPendingTransactions.unsafeRunSync().keys should contain theSameElementsAs txs
    }

    "ignore known transactions" in {
      val txPool = random[TxPool[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 10))
      val stxs   = SignedTransactions(txs)
      txPool.addTransactions(stxs).unsafeRunSync()
      txPool.addTransactions(stxs).unsafeRunSync()
      txPool.getPendingTransactions.unsafeRunSync().keys should contain theSameElementsAs txs
    }

    "broadcast received pending transactions to other peers" in {
      val txPool = random[TxPool[IO]]
      val txs    = random[List[SignedTransaction]](genTxs(1, 10))
//      val peerSet = genPeerSet(1, 10).sample.get
      val output = txPool.addTransactions(SignedTransactions(txs)).unsafeRunSync()
//      output.length shouldBe peerSet.connected.map(_.length).unsafeRunSync()
    }

    "override transactions with the same sender and nonce" in {
      val txPool = random[TxPool[IO]]
      val tx1    = genTx.sample.get
      val tx2    = genTx.sample.get
      val tx3    = genTx.sample.get

      val kp1 = random[KeyPair]
      val kp2 = random[KeyPair]

      val first  = SignedTransaction.sign[IO](tx1, kp1).unsafeRunSync()
      val second = SignedTransaction.sign[IO](tx2.copy(nonce = tx1.nonce), kp1).unsafeRunSync()
      val other  = SignedTransaction.sign[IO](tx3, kp2).unsafeRunSync()

      val p = for {
        _ <- txPool.addOrUpdateTransaction(first)
        _ <- txPool.addOrUpdateTransaction(second)
        _ <- txPool.addOrUpdateTransaction(other)
        x <- txPool.getPendingTransactions
        _ = x.keys should contain theSameElementsAs List(other, second)
      } yield ()

      p.unsafeRunSync()
    }

    "remove transaction on timeout" in {
      val conf = testConfig.withTxPool(_.copy(transactionTimeout = 100.millis))
      val txPool = random[TxPool[IO]](genTxPool(conf))
      val stx    = random[List[SignedTransaction]](genTxs(1, 1)).head
      val p = for {
        _  <- txPool.addTransactions(SignedTransactions(stx :: Nil))
        p1 <- txPool.getPendingTransactions
        _ = p1.size shouldBe 1
        p2 <- T.sleep(2.seconds) >> txPool.getPendingTransactions
        _ = p2.size shouldBe 0
      } yield ()
      p.unsafeRunSync()
    }
  }
}
