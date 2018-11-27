package jbok.core.config

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.config.Configs._
import pureconfig._
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._

class ConfigLoaderSpec extends JbokSpec {
  "ConfigLoader" should {
    "load genesis" in {
      val genesis = ConfigLoader.loadGenesis
      println(genesis)
    }

    "load datadir" in {
      val dir = ConfigLoader.config.getString("datadir")
      println(dir)
    }

    "load keystore" in {
      val keystore = ConfigLoader.loadOrThrow[KeyStoreConfig]("keystore")
      println(keystore)
    }

    "load peer" in {
      val peer = ConfigLoader.loadOrThrow[PeerManagerConfig]("peer")
      println(peer)
    }

    "load sync" in {
      val sync = ConfigLoader.loadOrThrow[SyncConfig]("sync")
      println(sync)
    }

    "load txPool" in {
      val txPool = ConfigLoader.loadOrThrow[TxPoolConfig]("txPool")
      println(txPool)
    }

    "load mining" in {
      val mining = ConfigLoader.loadOrThrow[MiningConfig]("mining")
      println(mining)
    }

    "load rpc" in {
      val rpc = ConfigLoader.loadOrThrow[RpcConfig]("rpc")
      println(rpc)
    }

    "load full" in {
      val full = for {
        datadir  <- IO(ConfigLoader.config.getString("datadir"))
        keystore <- IO(ConfigLoader.loadOrThrow[KeyStoreConfig]("keystore"))
        peer     <- IO(ConfigLoader.loadOrThrow[PeerManagerConfig]("peer"))
        sync     <- IO(ConfigLoader.loadOrThrow[SyncConfig]("sync"))
        txPool   <- IO(ConfigLoader.loadOrThrow[TxPoolConfig]("txPool"))
        mining   <- IO(ConfigLoader.loadOrThrow[MiningConfig]("mining"))
        rpc      <- IO(ConfigLoader.loadOrThrow[RpcConfig]("rpc"))
      } yield
        FullNodeConfig(
          datadir,
          keystore,
          peer,
          sync,
          txPool,
          mining,
          rpc,
          BlockChainConfig(),
          DaoForkConfig()
        )

      println(full.unsafeRunSync())
    }
  }
}
