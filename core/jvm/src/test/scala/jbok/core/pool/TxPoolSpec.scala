package jbok.core.pool

import cats.implicits._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.config.Configs.TxPoolConfig
import jbok.core.models.SignedTransaction
import jbok.core.testkit._
import jbok.crypto.testkit._

import scala.concurrent.duration._

class TxPoolSpec extends JbokSpec {
  implicit val consensusFixture = defaultFixture()

  "TxPool" should {
    "store pending transactions" in {
      val txPool = genTxPool().sample.get
      val txs    = genTxs(1, 10).sample.get
      txPool.addTransactions(txs).unsafeRunSync()
      txPool.getPendingTransactions.unsafeRunSync().map(_.stx) shouldBe txs
    }

    "ignore known transactions" in {
      val txPool = genTxPool().sample.get
      val txs    = genTxs(1, 10).sample.get
      txPool.addTransactions(txs).unsafeRunSync()
      txPool.addTransactions(txs).unsafeRunSync()
      txPool.getPendingTransactions.unsafeRunSync().map(_.stx) shouldBe txs
    }

    "broadcast received pending transactions to other peers" in {
      val txPool  = genTxPool().sample.get
      val txs     = genTxs(1, 10).sample.get
      val peerSet = genPeerSet(1, 10).sample.get
      val output  = txPool.addTransactions(txs, peerSet).unsafeRunSync()
      output.length shouldBe peerSet.connected.map(_.length).unsafeRunSync()
    }

    "override transactions with the same sender and nonce" in {
      val tx1 = genTx.sample.get
      val tx2 = genTx.sample.get
      val tx3 = genTx.sample.get

      val kp1 = genKeyPair.sample.get
      val kp2 = genKeyPair.sample.get

      val first  = SignedTransaction.sign(tx1, kp1)
      val second = SignedTransaction.sign(tx2.copy(nonce = tx1.nonce), kp1)
      val other  = SignedTransaction.sign(tx3, kp2)

      val txPool = genTxPool().sample.get

      val p = for {
        _ <- txPool.addOrUpdateTransaction(first)
        _ <- txPool.addOrUpdateTransaction(second)
        _ <- txPool.addOrUpdateTransaction(other)
        x <- txPool.getPendingTransactions
        _ = x.map(_.stx) shouldBe List(other, second)
      } yield ()

      p.unsafeRunSync()
    }

    "remove transaction on timeout" in {
      val config = TxPoolConfig().copy(transactionTimeout = 100.millis)
      val txPool = genTxPool(config).sample.get
      val stx    = arbSignedTransaction.arbitrary.sample.get
      val p = for {
        _  <- txPool.addTransactions(stx :: Nil)
        p1 <- txPool.getPendingTransactions
        _ = p1.length shouldBe 1
        p2 <- T.sleep(2.seconds) *> txPool.getPendingTransactions
        _ = p2.length shouldBe 0
      } yield ()
      p.unsafeRunSync()
    }
  }
}
