package jbok.app.api

import java.net._

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2._
import jbok.app.client.JbokClient
import jbok.core.config.Configs.FullNodeConfig
import jbok.core.config.GenesisConfig
import jbok.core.keystore.KeyStorePlatform
import jbok.core.models.{Account, Address, UInt256}
import jbok.core.mining.TxGen
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
                   nTx: Int) {
  private[this] val log = jbok.common.log.getLogger("TestNetTxGen")

  implicit val chainId: BigInt = genesisConfig.chainId

  private def getAccounts(addresses: List[Address], jbokClient: JbokClient): IO[List[Account]] =
    for {
      accounts <- addresses.traverse[IO, Account](jbokClient.public.getAccount(_, BlockParam.Latest))
      nonces   <- addresses.traverse[IO, BigInt](jbokClient.public.getEstimatedNonce)
      newAccounts = accounts.zip(nonces).map {
        case (account, nonce) => account.copy(nonce = UInt256(nonce))
      }
    } yield newAccounts

  private def stxStream: Stream[IO, Unit] = {
    def submitStxsToNetwork =
      for {
        cs <- clients.get
        client = cs.values.toList.head
        accounts <- getAccounts(keyPairs.map(Address.apply), client)
        stxs     <- TxGen.genTxs(nTx, keyPairs.zip(accounts).toMap)
        _        <- stxs.traverse[IO, ByteVector](stx => client.public.sendSignedTransaction(stx))
        _ = log.info(s"send ${nTx} stxs to network.")
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
      jbokClients <- Stream.eval(fullNodeConfigs.traverse[IO, JbokClient](x =>
        jbok.app.client.JbokClient(new URI(s"ws://${x.rpc.host}:${x.rpc.port}"))))
      _ <- Stream.eval(clients.update(_ ++ fullNodeConfigs.map(_.identity).zip(jbokClients).toMap))
      _ <- Stream.eval(T.sleep(3.seconds))
      _ <- stxStream
    } yield ()

}

object TestNetTxGen {
  private def loadKeyPairs(pathes: List[String]): IO[List[KeyPair]] =
    pathes
      .traverse[IO, List[KeyPair]](path =>
        for {
          ksp       <- KeyStorePlatform[IO](path)
          addresses <- ksp.listAccounts
          keyPairs <- addresses.traverse[IO, KeyPair] { address =>
            ksp.unlockAccount(address, "").map(_.keyPair)
          }
        } yield keyPairs)
      .map(_.flatten)

  private def loadGenessisConfig(pathes: List[String]): IO[GenesisConfig] =
    for {
      genesisList <- pathes.traverse[IO, GenesisConfig](path => GenesisConfig.fromFile(path))
      genesis <- if (genesisList.toSet.size == 1) {
        genesisList.head.pure[IO]
      } else {
        IO.raiseError(new Exception("nodes incompatible."))
      }
    } yield genesis

  private def loadFullNodeConfigs(pathes: List[String]): List[FullNodeConfig] =
    pathes.map(path => FullNodeConfig.fromJson(File(path).lines.mkString("\n")))

  private def parseFileDir: IO[(List[String], List[String], List[String])] = {
    val homePath = System.getProperty("user.home")
    val jbokPath = homePath + "/.jbok"
    for {
      dir <- IO { File.apply(jbokPath) }
      files <- if (!dir.exists || !dir.isDirectory || dir.list.isEmpty) {
        IO.raiseError(new Exception(s"not a jbok home dir: ${jbokPath}."))
      } else {
        IO {
          dir.list.toList.sortBy(_.toString)
        }
      }
      keyPairPath         = files.map(_ / "keystore").map(_.toString)
      genesisConfigPath   = files.map(_ / "genesis.json").map(_.toString)
      fullNodeConfigsPath = files.map(_ / "app.json").map(_.toString)
    } yield (keyPairPath, genesisConfigPath, fullNodeConfigsPath)
  }

  def apply(nTx: Int = 2): IO[TestNetTxGen] =
    for {
      (keyPairDir, genesisDir, fullNodeDir) <- parseFileDir
      keyPairs                              <- loadKeyPairs(keyPairDir)
      genesisConfig                         <- loadGenessisConfig(genesisDir)
      fullNodeConfigs                       <- loadFullNodeConfigs(fullNodeDir).pure[IO]
      clients                               <- Ref.of[IO, Map[String, JbokClient]](Map.empty)
    } yield new TestNetTxGen(clients, genesisConfig, fullNodeConfigs, keyPairs, nTx)
}
