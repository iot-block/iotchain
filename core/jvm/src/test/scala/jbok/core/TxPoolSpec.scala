package jbok.core

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.core.messages.{Messages, SignedTransactions}
import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.crypto.signature.{Ed25519, KeyPair, SecP256k1}
import jbok.network.execution._
import scodec.bits.ByteVector

import scala.concurrent.duration._

trait TxPoolFixture extends PeerManageFixture {
  val txPoolConfig = TxPoolConfig()
  val txPool = TxPool[IO](pm1, txPoolConfig).unsafeRunSync()
}

class TxPoolSpec extends JbokSpec {
  val tx = Transaction(1, 1, 1, Some(Address(ByteVector.fromInt(42))), 10, ByteVector.empty)

  def newStx(
      nonce: BigInt = 0,
      tx: Transaction,
      keyPair: KeyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
  ): SignedTransaction =
    SignedTransaction.sign(tx, keyPair, Some(0x31))

  "tx pool" should {
    "codec SignedTransactions" in {
      val stxs = SignedTransactions((1 to 10).toList.map(i => newStx(i, tx)))
      val bytes = Messages.encode(stxs)
      Messages.decode(bytes).require shouldBe stxs
    }

    "store pending transactions received from peers" in new TxPoolFixture {
      val msg = SignedTransactions((1 to 10).toList.map(i => newStx(i, tx)))

      val p = for {
        _ <- connect
        _ <- txPool.start
        _ <- pm2.broadcast(msg)
        _ <- IO(Thread.sleep(200))
        x <- txPool.getPendingTransactions
        _ = x.length shouldBe 10
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "ignore known transactions" in new TxPoolFixture {
      val msg = SignedTransactions(List(newStx(1, tx)))

      val p = for {
        _ <- connect
        _ <- txPool.start
        _ <- pm2.broadcast(msg)
        _ = println("pm2")
        _ <- pm3.broadcast(msg)
        _ = println("pm3")
        _ <- IO(Thread.sleep(200))
        p <- txPool.getPendingTransactions
        _ = p.length shouldBe 1
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "broadcast received pending transactions to other peers" in new TxPoolFixture {
      val stxs = SignedTransactions(newStx(1, tx) :: Nil)

      val p = for {
        _ <- connect
        _ <- txPool.start
        _ <- txPool.addTransactions(stxs.txs)
        x <- peerManagers.tail.traverse(_.subscribeMessages().take(1).compile.toList)
        _ = x.flatten.head.message shouldBe stxs
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "override transactions with the same sender and nonce" in new TxPoolFixture {
      val keyPair1 = SecP256k1.generateKeyPair[IO].unsafeRunSync()
      val keyPair2 = SecP256k1.generateKeyPair[IO].unsafeRunSync()
      val first = newStx(1, tx, keyPair1)
      val second = newStx(1, tx.copy(value = 2 * tx.value), keyPair1)
      val other = newStx(1, tx, keyPair2)

      val p = for {
        _ <- connect
        _ <- txPool.start
        _ <- txPool.addOrUpdateTransaction(first)
        _ <- txPool.addOrUpdateTransaction(other)
        _ <- txPool.addOrUpdateTransaction(second)
        _ <- IO(Thread.sleep(200))
        x <- txPool.getPendingTransactions
        _ = x.map(_.stx) shouldBe List(second, other)
        _ <- stopAll
      } yield ()

      p.unsafeRunSync()
    }

    "remove transaction on timeout" in new TxPoolFixture {
      val config = TxPoolConfig().copy(transactionTimeout = 100.millis)
      override val txPool = TxPool[IO](pm1, config).unsafeRunSync()

      val stx = newStx(0, tx)
      val p = for {
        _ <- txPool.start
        _ <- txPool.addTransactions(stx :: Nil)
        p1 <- txPool.getPendingTransactions
        _ = p1.length shouldBe 1
        p2 <- IO(Thread.sleep(200)) *> txPool.getPendingTransactions
        _ = p2.length shouldBe 0
      } yield ()

      p.unsafeRunSync()
    }

    "notify other peers about received transactions and handle removal" in {}
  }
}
