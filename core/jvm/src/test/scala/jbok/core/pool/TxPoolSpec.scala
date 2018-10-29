package jbok.core.pool

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.execution._
import jbok.core.messages.{Message, SignedTransactions}
import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.core.peer.PeersFixture
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1
import scodec.bits.ByteVector

import scala.concurrent.duration._

class TxPoolFixture(n: Int = 3, txPoolConfig: TxPoolConfig = TxPoolConfig()) extends PeersFixture(n) {
  val txPool = TxPool[IO](pms.head.pm, txPoolConfig).unsafeRunSync()
}

class TxPoolSpec extends JbokSpec {
  val tx = Transaction(1, 1, 1, Some(Address(ByteVector.fromInt(42))), 10, ByteVector.empty)

  def newStx(
      nonce: BigInt = 0,
      tx: Transaction,
      keyPair: KeyPair = SecP256k1.generateKeyPair().unsafeRunSync()
  ): SignedTransaction =
    SignedTransaction.sign(tx, keyPair, Some(0x3d.toByte))

  "TxPool" should {
    "codec SignedTransactions" in {
      val stxs  = SignedTransactions((1 to 10).toList.map(i => newStx(i, tx)))
      val bytes = Message.encode(stxs)
      Message.decode(bytes).require shouldBe stxs
    }

    "store pending transactions received from peers" in new TxPoolFixture() {
      val msg = SignedTransactions((1 to 10).toList.map(i => newStx(i, tx)))

      val p = for {
        _ <- startAll
        _ <- txPool.start
        _ <- pms.last.pm.broadcast(msg)
        _ <- T.sleep(2.second)
        x <- txPool.getPendingTransactions
        _ = x.length shouldBe 10
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "ignore known transactions" in new TxPoolFixture() {
      val msg = SignedTransactions(List(newStx(1, tx)))

      val p = for {
        _ <- startAll
        _ <- txPool.start
        _ <- pms.tail.traverse(_.pm.broadcast(msg))
        _ <- T.sleep(1.second)
        p <- txPool.getPendingTransactions
        _ = p.length shouldBe 1
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "broadcast received pending transactions to other peers" in new TxPoolFixture() {
      val stxs = SignedTransactions(newStx(1, tx) :: Nil)

      val p = for {
        _ <- startAll
        _ <- txPool.start
        _ <- txPool.addTransactions(stxs.txs)
        x <- pms.tail.traverse(_.pm.subscribe.take(1).compile.toList)
        _ = x.flatten.head._2 shouldBe stxs
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "override transactions with the same sender and nonce" in new TxPoolFixture() {
      val keyPair1 = SecP256k1.generateKeyPair().unsafeRunSync()
      val keyPair2 = SecP256k1.generateKeyPair().unsafeRunSync()
      val first    = newStx(1, tx, keyPair1)
      val second   = newStx(1, tx.copy(value = 2 * tx.value), keyPair1)
      val other    = newStx(1, tx, keyPair2)

      val p = for {
        _ <- startAll
        _ <- txPool.start
        _ <- txPool.addOrUpdateTransaction(first)
        _ <- txPool.addOrUpdateTransaction(other)
        _ <- txPool.addOrUpdateTransaction(second)
        _ <- T.sleep(2.seconds)
        x <- txPool.getPendingTransactions
        _ = x.map(_.stx) shouldBe List(second, other)
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "remove transaction on timeout" in new TxPoolFixture(
      txPoolConfig = TxPoolConfig().copy(transactionTimeout = 100.millis)
    ) {
      val stx = newStx(0, tx)
      val p = for {
        _  <- txPool.start
        _  <- txPool.addTransactions(stx :: Nil)
        p1 <- txPool.getPendingTransactions
        _ = p1.length shouldBe 1
        p2 <- T.sleep(2.seconds) *> txPool.getPendingTransactions
        _ = p2.length shouldBe 0
      } yield ()

      p.unsafeRunSync()
    }

    "notify other peers about received transactions and handle removal" in {}
  }
}
