package jbok.core.config

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.config.Configs._
import pureconfig.generic.auto._
import io.circe.syntax._
import io.circe.generic.auto._
import jbok.codec.json.implicits._
import jbok.core.config.ConfigLoader._

class ConfigLoaderSpec extends JbokSpec {
  "ConfigLoader" should {
    "load genesis" in {
      val genesis = ConfigLoader.loadGenesis
      println(genesis.asJson.spaces2)
    }

    "load datadir" in {
      val dir = ConfigLoader.config.getString("datadir")
      println(dir)
    }

    "load history" in {
      val history = ConfigLoader.loadOrThrow[HistoryConfig]("history")
      println(history.asJson.spaces2)
    }

    "load keystore" in {
      val keystore = ConfigLoader.loadOrThrow[KeyStoreConfig]("keystore")
      println(keystore.asJson.spaces2)
    }

    "load peer" in {
      val peer = ConfigLoader.loadOrThrow[PeerConfig]("peer")
      println(peer.asJson.spaces2)
    }

    "load sync" in {
      val sync = ConfigLoader.loadOrThrow[SyncConfig]("sync")
      println(sync.asJson.spaces2)
    }

    "load txPool" in {
      val txPool = ConfigLoader.loadOrThrow[TxPoolConfig]("txPool")
      println(txPool.asJson.spaces2)
    }

    "load mining" in {
      val mining = ConfigLoader.loadOrThrow[MiningConfig]("mining")
      println(mining.asJson.spaces2)
    }

    "load rpc" in {
      val rpc = ConfigLoader.loadOrThrow[RpcConfig]("rpc")
      println(rpc.asJson.spaces2)
    }

    "load full" in {
      val full = for {
        datadir  <- IO(ConfigLoader.config.getString("datadir"))
        history  <- IO(ConfigLoader.loadOrThrow[HistoryConfig]("history"))
        keystore <- IO(ConfigLoader.loadOrThrow[KeyStoreConfig]("keystore"))
        peer     <- IO(ConfigLoader.loadOrThrow[PeerConfig]("peer"))
        sync     <- IO(ConfigLoader.loadOrThrow[SyncConfig]("sync"))
        txPool   <- IO(ConfigLoader.loadOrThrow[TxPoolConfig]("txPool"))
        mining   <- IO(ConfigLoader.loadOrThrow[MiningConfig]("mining"))
        rpc      <- IO(ConfigLoader.loadOrThrow[RpcConfig]("rpc"))
      } yield
        FullNodeConfig(
          datadir,
          history,
          keystore,
          peer,
          sync,
          txPool,
          mining,
          rpc
        )

      println(full.unsafeRunSync().asJson.spaces2)
    }
  }
}
