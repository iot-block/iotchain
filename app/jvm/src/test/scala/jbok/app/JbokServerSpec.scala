package jbok.app

import java.net.InetSocketAddress

import cats.effect.IO
import fs2.Pipe
import jbok.JbokSpec
import jbok.app.api.FilterManager
import jbok.app.api.impl.{PrivateApiImpl, PublicApiImpl}
import jbok.core.config.Configs.{BlockChainConfig, FilterConfig, MiningConfig}
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.keystore.KeyStoreFixture
import jbok.core.mining.BlockMinerFixture
import jbok.network.rpc.RpcServer
import jbok.network.server.{Server, WSServerBuilder}
import jbok.network.execution._

import scala.io.StdIn

class JbokServerSpec extends BlockMinerFixture(new CliqueFixture {}) with JbokSpec with KeyStoreFixture {
  val privateApiImpl = PrivateApiImpl(
    keyStore,
    history,
    BlockChainConfig(),
    txPool
  ).unsafeRunSync()

  val filterManager = FilterManager(miner, keyStore, FilterConfig()).unsafeRunSync()

  val publicApiImpl = PublicApiImpl(
    history,
    BlockChainConfig(),
    MiningConfig(),
    miner,
    keyStore,
    filterManager,
    1
  ).unsafeRunSync()

  import jbok.network.rpc.RpcServer._

  val rpcServer                            = RpcServer().unsafeRunSync()
    .mountAPI(publicApiImpl)

  val bind                                 = new InetSocketAddress("localhost", 8888)
  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val server: Server[IO, String]           = Server(WSServerBuilder[IO, String], bind, serverPipe).unsafeRunSync()

  server.start.unsafeRunSync()
  println(s"rpc server started on ${bind}, press any key to stop")
  StdIn.readLine()

  override protected def afterAll(): Unit =
    server.stop.unsafeRunSync()
}
