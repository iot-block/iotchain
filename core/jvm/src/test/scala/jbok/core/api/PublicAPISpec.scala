package jbok.core.api

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.configs.FilterConfig
import jbok.core.keystore.KeyStoreFixture
import jbok.core.ledger.OmmersPool
import jbok.core.mining.BlockGeneratorFixture
import jbok.core.{PeerManageFixture, TxPool, TxPoolConfig}
import jbok.network.client.Client
import jbok.network.execution._
import jbok.network.server.Server
import jbok.network.transport.WSTransport
import jbok.network.{JsonRPCService, NetAddress}

trait MainAPIFixture extends BlockGeneratorFixture with PeerManageFixture with KeyStoreFixture {
  val ommersPool = OmmersPool[IO](blockChain).unsafeRunSync()
  val txPool = TxPool[IO](pm1, TxPoolConfig()).unsafeRunSync()
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
  val mainApiImpl = PublicAPI[IO](
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

  val service = JsonRPCService[IO].mountAPI[PublicAPI[IO]](mainApiImpl)
  val addr = NetAddress("localhost", 9999)
  val server = Server[IO](addr, service).unsafeRunSync()
  val transport = WSTransport[IO](addr).unsafeRunSync()
  val client = Client[IO](transport)
  val api = client.useAPI[PublicAPI[IO]]

  server.start.unsafeRunSync()
  client.start.unsafeRunSync()
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

  override protected def afterAll(): Unit = {
    client.stop.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
