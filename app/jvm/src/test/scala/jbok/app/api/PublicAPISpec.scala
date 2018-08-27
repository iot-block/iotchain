package jbok.core.api

import java.net.InetSocketAddress

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.core.configs.FilterConfig
import jbok.core.keystore.KeyStoreFixture
import jbok.core.ledger.OmmersPool
import jbok.core.mining.BlockGeneratorFixture
import jbok.core.{PeerManageFixture, TxPool, TxPoolConfig}
import jbok.network.client.{Client, WebSocketClientBuilder}
import jbok.network.execution._
import jbok.network.rpc.{RpcClient, RpcServer}
import jbok.network.server.{Server, WebSocketServerBuilder}

trait MainAPIFixture extends BlockGeneratorFixture with PeerManageFixture with KeyStoreFixture {
  val ommersPool   = OmmersPool[IO](blockChain).unsafeRunSync()
  val txPool       = TxPool[IO](pm1, TxPoolConfig()).unsafeRunSync()
  val filterConfig = FilterConfig()
  val txPoolConfig = TxPoolConfig()
  val filterManager = FilterManager[IO](
    blockChain,
    blockGenerator,
    keyStore,
    txPool,
    filterConfig,
    txPoolConfig
  ).unsafeRunSync()

  val version = 1
  val mainApiImpl = PublicAPI(
    blockChain,
    blockChainConfig,
    ommersPool,
    txPool,
    ledger,
    blockGenerator,
    keyStore,
    filterManager,
    miningConfig,
    version
  ).unsafeRunSync()

  import RpcServer._
  val rpcServer                            = RpcServer().unsafeRunSync().mountAPI(mainApiImpl)
  val bind                                 = new InetSocketAddress("localhost", 9999)
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val server: Server[IO, String]           = Server(WebSocketServerBuilder[IO, String], bind, serverPipe).unsafeRunSync()
  val client: Client[IO, String]           = Client(WebSocketClientBuilder[IO, String], bind).unsafeRunSync()
  val api: PublicAPI                       = RpcClient[IO](client).useAPI[PublicAPI]
}

class PublicAPISpec extends JbokSpec with MainAPIFixture {
  "MainAPI" should {
    "get bestBlockNumber" in {
      val x = api.bestBlockNumber.unsafeRunSync()
      x shouldBe Right(0)
    }

    "get version" in {
      val x = api.protocolVersion.unsafeRunSync()
      x shouldBe Right("0x1")
    }

    "getBlockTransactionCountByHash with None when the requested block isn't in the blockchain" ignore {}

    "getTransactionByBlockHashAndIndex" ignore {}

    "getBlockByNumBer" ignore {}

    "getBlockByHash" ignore {}

    "getUncleByBlockHashAndIndex" ignore {}

    "getUncleByBlockNumberAndIndex" ignore {}

    "return syncing info" ignore {}

    "accept submitted correct PoW" ignore {}

    "reject submitted correct PoW when header is no longer in cache" ignore {}

    "return average gas price" ignore {}

    "return account recent transactions in newest -> oldest order" ignore {}
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
