package jbok.app
import java.net.InetSocketAddress
import java.nio.file.{Path, Paths, StandardOpenOption}

import better.files.File
import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import ch.qos.logback.classic.{Level, Logger}
import jbok.core.config.Configs
import jbok.core.config.Configs._
import jbok.network.server.Server
import org.rogach.scallop._
import org.slf4j.LoggerFactory

import scala.language.reflectiveCalls

class Conf(arguments: List[String]) extends ScallopConf(arguments) {
  appendDefaultToDescription = true

  val buildVersion = getClass.getPackage.getImplementationVersion

  version(s"v${buildVersion} © 2018 The JBOK Authors")
  banner("""
           | .----------------.  .----------------.  .----------------.  .----------------.
           || .--------------. || .--------------. || .--------------. || .--------------. |
           || |     _____    | || |   ______     | || |     ____     | || |  ___  ____   | |
           || |    |_   _|   | || |  |_   _ \    | || |   .'    `.   | || | |_  ||_  _|  | |
           || |      | |     | || |    | |_) |   | || |  /  .--.  \  | || |   | |_/ /    | |
           || |   _  | |     | || |    |  __'.   | || |  | |    | |  | || |   |  __'.    | |
           || |  | |_' |     | || |   _| |__) |  | || |  \  `--'  /  | || |  _| |  \ \_  | |
           || |  `.___.'     | || |  |_______/   | || |   `.____.'   | || | |____||____| | |
           || |              | || |              | || |              | || |              | |
           || '--------------' || '--------------' || '--------------' || '--------------' |
           | '----------------'  '----------------'  '----------------'  '----------------'
           |
           |""".stripMargin)
  footer("Choose a specific sub-command to start")

  val web = new Subcommand("web") {
    val host = opt[String](default = "localhost".some, descr = "web server binding host", noshort = true)
    val port = opt[Int](default = 9090.some, descr = "web server binding port", noshort = true)

    // hidden
    val help = opt[Boolean](noshort = true, hidden = true)
  }
  addSubcommand(web)

  val node = new Subcommand("node") {}
  addSubcommand(node)

  val console = new Subcommand("console") {
    // hidden
    val help = opt[Boolean](noshort = true, hidden = true)
  }
  addSubcommand(console)

  // general options
  val verbosity =
    opt[Int](default = 2.some, descr = "logging verbosity: 0=trace, 1=debug, 2=info, 3=warn, 4=error", noshort = true)

  // full node config
  val datadir   = opt[String](default = Configs.defaultRootDir.some, descr = "root data directory", noshort = true)
  val networkid = opt[Int](default = 1.some, descr = "network identifier", noshort = true)
  val identity  = opt[String](default = "my-node".some, descr = "custom node name", noshort = true)

  // rpc config
  val rpc     = opt[Boolean](default = false.some, descr = "enable rpc server", noshort = true)
  val rpchost = opt[String](default = "localhost".some, descr = "rpc server binding host", noshort = true)
  val rpcport = opt[Int](default = 10086.some, descr = "rpc server binding port", noshort = true)

  // peer manager config
  val port        = opt[Int](default = 30314.some, descr = "peer server binding port", noshort = true)
  val bootnodes   = opt[String](default = "".some, descr = "bootstrap node uris for P2P discovery", noshort = true)
  val maxincoming = opt[Int](default = 10.some, descr = "maximum number of incoming peers", noshort = true)
  val maxoutgoing = opt[Int](default = 10.some, descr = "maximum number of outgoing peers", noshort = true)
  val nodiscover  = opt[Boolean](descr = "disable the peer discovery mechanism", noshort = true)

  // mining config
  val mine = opt[Boolean](default = false.some, descr = "enable mining", noshort = true)

  // hidden
  val help = opt[Boolean](noshort = true, hidden = true)

  verify()

  val rpcConfig = RpcConfig(
    rpc(),
    rpchost(),
    rpcport()
  )

  val keystoreConfig = KeyStoreConfig(
    s"${datadir()}/keystore"
  )

  val peerManagerConfig = PeerManagerConfig(
    port = port(),
    host = "localhost",
    bootUris = bootnodes().split(",").toList
  )

  // chain specific config
  val blockChainConfig = BlockChainConfig()
  val daoForkConfig    = DaoForkConfig()
  val syncConfig       = SyncConfig()
  val miningConfig = MiningConfig(
    enabled = mine()
  )

  // assemble
  val config = FullNodeConfig(
    datadir = datadir(),
    rpc = rpcConfig,
    keystore = keystoreConfig,
    peer = peerManagerConfig,
    blockchain = blockChainConfig,
    daofork = daoForkConfig,
    sync = syncConfig,
    mining = miningConfig
  )
}

object MainApp extends IOApp {
  def lock(path: Path): IO[Unit] = IO {
    val file    = File(path).createIfNotExists(createParents = true)
    val channel = file.newFileChannel(StandardOpenOption.WRITE :: Nil)
    val lock    = channel.tryLock()
    if (lock == null) {
      println(s"process lock ${path} already used by another process, terminated")
      System.exit(-1)
    } else {
      sys.addShutdownHook {
        lock.release()
        channel.close()
        file.delete()
      }
    }
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val conf         = new Conf(args)
    val root: Logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
    conf.verbosity() match {
      case 0 => root.setLevel(Level.TRACE)
      case 1 => root.setLevel(Level.DEBUG)
      case 2 => root.setLevel(Level.INFO)
      case 3 => root.setLevel(Level.WARN)
      case 4 => root.setLevel(Level.ERROR)
      case _ => root.setLevel(Level.INFO)
    }

    conf.subcommand match {
      case Some(conf.node) =>
        for {
          _        <- lock(Paths.get(s"${conf.config.datadir}/LOCK"))
          fullNode <- FullNode.forConfig(conf.config)
          _        <- fullNode.start
          _        <- IO.never
        } yield ExitCode.Success

      case Some(conf.web) =>
        val bind = new InetSocketAddress(conf.web.host(), conf.web.port())
        for {
          fullNode <- FullNode.forConfig(conf.config)
          server   <- Server.http[IO](bind, fullNode.rpc.handle)
          code     <- server.serve.compile.drain.as(ExitCode.Success)
        } yield code

      case _ =>
        conf.printHelp()
        IO.pure(ExitCode.Error)
    }
  }
}
