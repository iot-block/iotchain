package jbok.app

import java.net.InetSocketAddress

import cats.effect.IO
import fs2.Pipe
import jbok.JbokSpec
import jbok.app.api.impl.{PrivateApiImpl, PublicApiImpl}
import jbok.app.api.{FilterManager, PrivateAPI, PublicAPI}
import jbok.common.execution._
import jbok.core.config.Configs.{BlockChainConfig, FilterConfig, MiningConfig}
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.keystore.KeyStoreFixture
import jbok.core.mining.BlockMinerFixture
import jbok.network.rpc.RpcServer
import jbok.network.server.Server

import scala.io.StdIn

class JbokServer extends BlockMinerFixture(new CliqueFixture {}) with KeyStoreFixture with JbokSpec {
  val bind = new InetSocketAddress("localhost", 8888)

  val filterManager = FilterManager(miner, keyStore, FilterConfig()).unsafeRunSync()

  val privateApiImpl = PrivateApiImpl(
    keyStore,
    history,
    BlockChainConfig(),
    txPool
  ).unsafeRunSync()

  val publicApiImpl = PublicApiImpl(
    history,
    BlockChainConfig(),
    MiningConfig(),
    miner,
    keyStore,
    filterManager,
    1
  ).unsafeRunSync()
  import RpcServer._
  val rpcServer =
    RpcServer()
      .unsafeRunSync()
      .mountAPI[PublicAPI](publicApiImpl)
      .mountAPI[PrivateAPI](privateApiImpl)

  val serverPipe: Pipe[IO, String, String] = rpcServer.pipe
  val p = for {
    server <- Server.websocket(bind, serverPipe)
    _      <- server.start
    _ = println(s"server listen on ${bind}, press any key to quit")
    _ = StdIn.readLine()
    _ <- server.stop
  } yield ()

  p.unsafeRunSync()
}
