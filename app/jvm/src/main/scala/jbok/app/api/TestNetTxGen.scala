package jbok.app.api

import java.net._

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import com.typesafe.config.ConfigFactory
import fs2._
import jbok.app.client.JbokClient
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.{ConfigLoader, GenesisConfig}
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.{Account, Address}
import jbok.crypto.signature.KeyPair
import jbok.sdk.api.BlockParam
import scodec.bits.ByteVector
import jbok.common.execution._
import better.files._

import scala.concurrent.duration._

class TestNetTxGen(clients: Ref[IO, Map[String, JbokClient]],
                   genesisConfig: GenesisConfig,
                   fullNodeConfigs: List[FullNodeConfig],
                   keyPairs: List[KeyPair],
) {
  private[this] val log = jbok.common.log.getLogger("TestNetTxGen")

  private def getAccounts(addresses: List[Address], jbokClient: JbokClient): IO[List[Account]] =
    addresses.traverse[IO, Account](jbokClient.public.getAccount(_, BlockParam.Latest))

  private def stxStream: Stream[IO, Unit] = {
    def submitStxsToNetwork =
      for {
        cs <- clients.get
        client = cs.values.toList.head
        accounts <- getAccounts(keyPairs.map(Address.apply), client)
        stxs     <- TxGen.genTxs(2, keyPairs.zip(accounts).toMap, genesisConfig.chainId)
        _        <- stxs.traverse[IO, ByteVector](stx => client.public.sendRawTransaction(stx.asBytes))
      } yield ()

    Stream
      .awakeEvery[IO](fullNodeConfigs.head.mining.period)
      .evalMap[IO, Unit] { _ =>
        submitStxsToNetwork
      }
      .handleErrorWith[IO, Unit] { err =>
        Stream.eval(IO.delay(log.error(err)))
      }
      .onFinalize[IO](IO.delay(log.info("stx stream stop.")))
  }

  def run: Stream[IO, Unit] =
    for {
      jbokClients <- Stream.eval(
        fullNodeConfigs.traverse[IO, JbokClient](x => jbok.app.client.JbokClient(new URI(x.rpc.addr.toString))))
      _ <- Stream.eval(clients.update(_ ++ fullNodeConfigs.map(_.identity).zip(jbokClients).toMap))
      _ <- Stream.eval(T.sleep(3.seconds))
      _ <- stxStream
    } yield ()

}

object TestNetTxGen {
  private def loadKeyPairs(path: String): IO[List[KeyPair]] =
    for {
      ksp       <- KeyStorePlatform[IO](path)
      addresses <- ksp.listAccounts
      keyPairs <- addresses.traverse[IO, KeyPair] { address =>
        ksp.unlockAccount(address, "").map(_.keyPair)
      }
    } yield keyPairs

  private def loadGenessisConfig(path: String): IO[GenesisConfig] = GenesisConfig.fromFile(path)

  private def loadFullNodeConfigs(paths: List[String]): IO[List[FullNodeConfig]] =
    paths
      .map(p => ConfigFactory.parseFile(new java.io.File(p)).getConfig("jbok"))
      .traverse[IO, FullNodeConfig](ConfigLoader.loadFullNodeConfig[IO])

  private def parseFileDir: IO[(String, String, List[String])] = {
    val homePath = System.getProperty("user.home")
    val path     = homePath + "/.jbok"
    for {
      dir <- IO { File.apply(path) }
      files <- if (!dir.exists || !dir.isDirectory || dir.list.isEmpty) {
        IO.raiseError(new Exception(s"not a jbok home dir: ${path}."))
      } else {
        IO {
          dir.list.toList.sortBy(_.toString)
        }
      }
      keyPairDir         = (files.head / "keystore").toString
      genesisConfigDir   = (files.head / "genesis.conf").toString
      fullNodeConfigsDir = files.map(_ / "jbok.conf").map(_.toString)
      _                  = println(fullNodeConfigsDir)
    } yield (keyPairDir, genesisConfigDir, fullNodeConfigsDir)
  }

  def apply(): IO[TestNetTxGen] =
    for {
      (keyPairDir, genesisDir, fullNodeDir) <- parseFileDir
      keyPairs                              <- loadKeyPairs(keyPairDir)
      genesisConfig                         <- loadGenessisConfig(genesisDir)
      fullNodeConfigs                       <- loadFullNodeConfigs(fullNodeDir)
      clients                               <- Ref.of[IO, Map[String, JbokClient]](Map.empty)
    } yield new TestNetTxGen(clients, genesisConfig, fullNodeConfigs, keyPairs)
}
