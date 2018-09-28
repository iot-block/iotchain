package jbok.app.api

import java.net.{InetSocketAddress, URI}

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.app.api.impl.PrivateApiImpl
import jbok.core.config.Configs.BlockChainConfig
import jbok.core.keystore.{KeyStoreFixture, Wallet}
import jbok.core.models.{Address, SignedTransaction}
import jbok.core.HistoryFixture
import jbok.core.pool.TxPoolFixture
import jbok.crypto.signature.{CryptoSignature, KeyPair}
import jbok.network.client.{Client, WebSocketClientBuilder}
import jbok.network.execution._
import jbok.network.rpc.{RpcClient, RpcServer}
import jbok.network.server.{Server, WebSocketServerBuilder}
import scodec.bits._

trait PrivateAPIFixture extends HistoryFixture with KeyStoreFixture with TxPoolFixture {
  val privateApiImpl = PrivateApiImpl(
    keyStore,
    history,
    BlockChainConfig(),
    txPool
  ).unsafeRunSync()

  import RpcServer._
  val rpcServer                            = RpcServer().unsafeRunSync().mountAPI(privateApiImpl)
  val bind                                 = new InetSocketAddress("localhost", 9998)
  val uri = new URI("ws://localhost:9998")
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val server: Server[IO, String]           = Server(WebSocketServerBuilder[IO, String], bind, serverPipe).unsafeRunSync()
  val client: Client[IO, String]           = Client(WebSocketClientBuilder[IO, String], uri).unsafeRunSync()
  val api: PrivateAPI                      = RpcClient[IO](client).useAPI[PrivateAPI]

  val prvKey     = hex"7a44789ed3cd85861c0bbf9693c7e1de1862dd4396c390147ecf1275099c6e6f"
  val address    = Address(hex"aa6826f00d01fe4085f0c3dd12778e206ce4e2ac")
  val passphrase = "aaa"
}

class PrivateAPISpec extends JbokSpec with PrivateAPIFixture {
  "private api" should {
    "import private keys" in {
      val p = for {
        x <- api.importRawKey(prvKey, passphrase)
        _ = x shouldBe address
      } yield ()

      p.unsafeRunSync()
    }

    "list accounts" in {
      val p = for {
        acc1 <- api.listAccounts
        _    <- api.newAccount("")
        acc2 <- api.listAccounts
        _ = acc2.length shouldBe acc1.length + 1
      } yield ()

      p.unsafeRunSync()
    }

    "return an error when trying to import an invalid key" in {
      val invalidKey = prvKey.tail
      val p = for {
        r <- api.importRawKey(invalidKey, passphrase).attempt
        _ = r.isLeft shouldBe true
      } yield ()

      p.unsafeRunSync()
    }

    "unlock an account given a correct passphrase" in {
      val p = for {
        _  <- api.importRawKey(prvKey, passphrase)
        b1 <- api.unlockAccount(address, "", None).attempt
        _ = b1.isLeft shouldBe true

        b2 <- api.unlockAccount(address, passphrase, None)
        _ = b2 shouldBe true
        _ <- api.lockAccount(address)
      } yield ()

      p.unsafeRunSync()
    }

    "send transaction with passphrase" in {
      val wallet                 = Wallet(address, KeyPair.Secret(prvKey))
      val nonce                  = 7
      val txValue                = 128000
      val tx                     = TransactionRequest(from = address, to = Some(Address(42)), value = Some(txValue))
      val stx: SignedTransaction = wallet.signTx(tx.toTransaction(nonce), None)
      val p = for {
        _ <- txPool.start
        _ <- api.importRawKey(prvKey, passphrase)

        r <- api.sendTransaction(tx, Some(passphrase))
        p <- txPool.getPendingTransactions
        _ = r shouldBe p.head.stx.hash

        r2 <- api.sendTransaction(tx, Some("wrongPassphrase")).attempt
        _ = r2.isLeft shouldBe true
      } yield ()

      p.unsafeRunSync()
    }

    "sign message" in {
      val message = hex"deadbeaf"
      val r       = hex"d237344891a90a389b7747df6fbd0091da20d1c61adb961b4491a4c82f58dcd2"
      val s       = hex"5425852614593caf3a922f48a6fe5204066dcefbf6c776c4820d3e7522058d00"
      val v       = 27.toByte
      val sig     = CryptoSignature(r, s, v)

      val p = for {
        _ <- api.importRawKey(prvKey, passphrase)

        x <- api.sign(message, address, Some("wrong")).attempt
        _ = x.isLeft shouldBe true

        x1 <- api.sign(message, address, Some(passphrase))
        _ = x1 shouldBe sig

        x2 <- api.sign(message, address, None).attempt
        _ = x2.isLeft shouldBe true

        _  <- api.unlockAccount(address, passphrase, None)
        x3 <- api.sign(message, address, None)
        _ = x3 shouldBe sig
      } yield ()

      p.unsafeRunSync()

    }

    "recover address form signed message" in {
      val sigAddress = Address(hex"12c2a3b877289050FBcfADC1D252842CA742BE81")
      val message    = hex"deadbeaf"
      val r          = hex"117b8d5b518dc428d97e5e0c6f870ad90e561c97de8fe6cad6382a7e82134e61"
      val s          = hex"396d881ef1f8bc606ef94b74b83d76953b61f1bcf55c002ef12dd0348edff24b"
      val v          = hex"1b".last

      val p = for {
        addr <- api.ecRecover(message, CryptoSignature(r, s, v))
        _ = addr shouldBe sigAddress
      } yield ()

      p.unsafeRunSync()
    }

    "allow to sign and recover the same message" in {
      val message = hex"deadbeaf"

      val p = for {
        _    <- api.importRawKey(prvKey, passphrase)
        sig  <- api.sign(message, address, Some(passphrase))
        addr <- api.ecRecover(message, sig)
        _ = addr shouldBe address
      } yield ()

      p.unsafeRunSync()
    }

    "change passphrase" in {
      val newPass = "newPass"
      val p = for {
        _ <- api.importRawKey(prvKey, passphrase)

        x1 <- api.changePassphrase(address, "wrong", newPass).attempt
        _ = x1.isLeft shouldBe true

        x2 <- api.changePassphrase(address, passphrase, newPass).attempt
        _ = x2.isRight shouldBe true

        r1 <- api.unlockAccount(address, passphrase, None).attempt
        _ = r1.isLeft shouldBe true

        r2 <- api.unlockAccount(address, newPass, None).attempt
        _ = r2.isRight shouldBe true
      } yield ()

      p.unsafeRunSync()
    }
  }

  override protected def beforeAll(): Unit = {
    server.start.unsafeRunSync()
    Thread.sleep(3000)
    client.start.unsafeRunSync()
  }

  override protected def afterAll(): Unit = {
    client.stop.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
