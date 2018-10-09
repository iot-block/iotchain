package jbok.app

import java.net.InetSocketAddress

import cats.effect.IO
import fs2.Pipe
import jbok.JbokSpec
import jbok.app.api.{FilterManager, PrivateAPI, PublicAPI}
import jbok.app.api.impl.{PrivateApiImpl, PublicApiImpl}
import jbok.core.config.Configs.{BlockChainConfig, FilterConfig, MiningConfig}
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.keystore.KeyStoreFixture
import jbok.core.mining.BlockMinerFixture
import jbok.network.rpc.RpcServer
import jbok.network.server.{Server, WSServerBuilder}
import jbok.network.execution._

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
  val server: Server[IO, String]           = Server(WSServerBuilder[IO, String], bind, serverPipe).unsafeRunSync()

  server.start.unsafeRunSync()
  println(s"server listen on ${bind}, press any key to quit")
  StdIn.readLine()
  server.stop.unsafeRunSync()
}
