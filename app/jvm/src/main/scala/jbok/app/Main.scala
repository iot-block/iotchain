package jbok.app
import java.net.InetSocketAddress
import java.security.SecureRandom

import better.files.File
import cats.effect.IO
import fs2._
import jbok.app.api.impl.{PrivateApiImpl, PublicApiImpl}
import jbok.app.api.{FilterManager, PrivateAPI, PublicAPI}
import jbok.core.config.Configs.{BlockChainConfig, FilterConfig, MiningConfig}
import jbok.core.consensus.poa.clique.CliqueFixture
import jbok.core.keystore.KeyStorePlatform
import jbok.core.mining.BlockMinerFixture
import jbok.common.execution._
import jbok.network.rpc.RpcServer
import jbok.network.server.{Server, WSServerBuilder}

import scala.io.StdIn

object Main extends App {
  val miner = new BlockMinerFixture(new CliqueFixture {})
  val secureRandom = new SecureRandom()
  val dir          = File.newTemporaryDirectory().deleteOnExit()
  val keyStore = KeyStorePlatform[IO](dir.pathAsString, secureRandom).unsafeRunSync()
  val bind = new InetSocketAddress("localhost", 8888)
  val filterManager = FilterManager(miner.miner, keyStore, FilterConfig()).unsafeRunSync()
  val privateApiImpl = PrivateApiImpl(
    keyStore,
    miner.history,
    BlockChainConfig(),
    miner.txPool
  ).unsafeRunSync()

  val publicApiImpl = PublicApiImpl(
    miner.history,
    BlockChainConfig(),
    MiningConfig(),
    miner.miner,
    keyStore,
    filterManager,
    1
  ).unsafeRunSync()
  import jbok.network.rpc.RpcServer._
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
