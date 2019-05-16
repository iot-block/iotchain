package jbok.app.txgen

import java.net._
import java.nio.file.Paths

import better.files._
import cats.effect.concurrent.Ref
import cats.effect._
import cats.implicits._
import fs2._
import jbok.app.JbokClient
import jbok.common.config.Config
import jbok.common.log.Logger
import jbok.core.config.Configs.CoreConfig
import jbok.core.config.GenesisConfig
import jbok.core.keystore.KeyStorePlatform
import jbok.core.mining.TxGen
import jbok.core.models.{Account, Address, UInt256}
import jbok.crypto.signature.KeyPair
import jbok.sdk.api.BlockParam

import scala.concurrent.duration._

final class TestNetTxGen[F[_]](
    clients: Ref[F, Map[String, JbokClient[F]]],
    genesisConfig: GenesisConfig,
    fullNodeConfigs: List[CoreConfig],
    keyPairs: List[KeyPair],
    nTx: Int
)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  implicit val chainId: BigInt = genesisConfig.chainId

  private def getAccounts(addresses: List[Address], jbokClient: JbokClient[F]): F[List[Account]] =
    for {
      accounts <- addresses.traverse(jbokClient.public.getAccount(_, BlockParam.Latest))
      nonces   <- addresses.traverse(jbokClient.public.getEstimatedNonce)
      newAccounts = accounts.zip(nonces).map {
        case (account, nonce) => account.copy(nonce = UInt256(nonce))
      }
    } yield newAccounts

  private def stxStream: Stream[F, Unit] = {
    def submitStxsToNetwork: F[Unit] =
      for {
        cs <- clients.get
        client = cs.values.toList.head
        accounts  <- getAccounts(keyPairs.map(Address.apply), client)
        (_, stxs) <- TxGen.genTxs[F](nTx, keyPairs.zip(accounts).toMap)
        _         <- stxs.traverse(stx => client.public.sendSignedTransaction(stx))
        _ = log.info(s"send ${nTx} stxs to network.")
      } yield ()

    Stream
      .fixedDelay[F](fullNodeConfigs.head.mining.period)
      .evalMap(_ => submitStxsToNetwork)
      .handleErrorWith { err =>
        Stream.eval(log.error("", err))
      }
      .onFinalize(F.delay(log.info("stx stream stop.")))
  }

  def run: Stream[F, Unit] =
    for {
      jbokClients <- Stream.resource(fullNodeConfigs.traverse(x => JbokClient[F](new URI(s"ws://${x.rpc.host}:${x.rpc.port}"))))
      _           <- Stream.eval(clients.update(_ ++ fullNodeConfigs.map(_.identity).zip(jbokClients).toMap))
      _           <- Stream.eval(T.sleep(3.seconds))
      _           <- stxStream
    } yield ()
}

object TestNetTxGen {
  private def loadKeyPairs[F[_]: Async](paths: List[String]): F[List[KeyPair]] =
    paths
      .traverse(path =>
        for {
          ksp       <- KeyStorePlatform[F](path)
          addresses <- ksp.listAccounts
          keyPairs <- addresses.traverse { address =>
            ksp.unlockAccount(address, "").map(_.keyPair)
          }
        } yield keyPairs)
      .map(_.flatten)

  private def loadGenessisConfig[F[_]](paths: List[String])(implicit F: Sync[F]): F[GenesisConfig] =
    for {
      genesisList <- paths.traverse(path => Config[F].read[GenesisConfig](Paths.get(path)))
      genesis <- if (genesisList.toSet.size == 1) {
        genesisList.head.pure[F]
      } else {
        F.raiseError(new Exception("nodes incompatible."))
      }
    } yield genesis

  private def loadFullNodeConfigs[F[_]: Sync](paths: List[String]): F[List[CoreConfig]] =
    paths.traverse(path => Config[F].read[CoreConfig](Paths.get(path)))

  private def parseFileDir[F[_]](implicit F: Sync[F]): F[(List[String], List[String], List[String])] = {
    val homePath = System.getProperty("user.home")
    val jbokPath = homePath + "/.jbok"
    for {
      dir <- F.delay(File.apply(jbokPath))
      files: List[File] <- if (!dir.exists || !dir.isDirectory || dir.list.isEmpty) {
        F.raiseError(new Exception(s"not a jbok home dir: ${jbokPath}."))
      } else {
        F.delay(dir.list.toList.sortBy(_.toString))
      }
      keyPairPath         = files.map(_ / "keystore").map(_.toString)
      genesisConfigPath   = files.map(_ / "genesis.json").map(_.toString)
      fullNodeConfigsPath = files.map(_ / "app.json").map(_.toString)
    } yield (keyPairPath, genesisConfigPath, fullNodeConfigsPath)
  }

  def apply[F[_]](nTx: Int = 2)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): F[TestNetTxGen[F]] =
    for {
      (keyPairDir, genesisDir, fullNodeDir) <- parseFileDir
      keyPairs                              <- loadKeyPairs[F](keyPairDir)
      genesisConfig                         <- loadGenessisConfig[F](genesisDir)
      fullNodeConfigs                       <- loadFullNodeConfigs[F](fullNodeDir)
      clients                               <- Ref.of[F, Map[String, JbokClient[F]]](Map.empty)
    } yield new TestNetTxGen(clients, genesisConfig, fullNodeConfigs, keyPairs, nTx)
}
