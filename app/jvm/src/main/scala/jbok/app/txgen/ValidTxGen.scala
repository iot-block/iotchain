package jbok.app.txgen

import cats.effect._
import cats.implicits._
import fs2._
import jbok.core.config.CoreConfig
import jbok.common.log.Logger
import jbok.core.api.JbokClient
import jbok.core.mining.TxGen
import jbok.core.models.{Account, Address, UInt256}
import jbok.crypto.signature.KeyPair

final class ValidTxGen[F[_]](config: CoreConfig, client: JbokClient[F], keyPairs: List[KeyPair])(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  implicit val chainId = config.genesis.chainId

  val addresses: List[Address] = keyPairs.map(Address.apply)

  def getAccounts: F[List[Account]] =
    for {
      accounts <- addresses.traverse(client.account.getAccount(_))
      nonces   <- addresses.traverse(client.account.getEstimatedNonce)
      newAccounts = accounts.zip(nonces).map {
        case (account, nonce) => account.copy(nonce = UInt256(nonce))
      }
    } yield newAccounts

  def submitTransactions(nTx: Int = 10): F[Unit] =
    for {
      accounts  <- getAccounts
      (_, stxs) <- TxGen.genTxs[F](nTx, keyPairs.zip(accounts).toMap)
      _         <- stxs.traverse(stx => client.transaction.sendTx(stx))
      _         <- log.info(s"send ${nTx} transactions to network")
    } yield ()

  val stream: Stream[F, Unit] =
    Stream.eval(log.i(s"starting TxGen")) ++
      Stream
        .fixedDelay[F](config.mining.period)
        .evalMap(_ => submitTransactions())
}
//
//final class TestNetTxGen[F[_]](
//    clients: Ref[F, List[JbokClient[F]]],
//    genesisConfig: GenesisConfig,
//    fullNodeConfigs: List[FullConfig],
//    keyPairs: List[KeyPair],
//    nTx: Int
//)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) {
//  private[this] val log = Logger[F]
//
//  implicit val chainId: BigInt = genesisConfig.chainId
//
//  private def getAccounts(addresses: List[Address], jbokClient: JbokClient[F]): F[List[Account]] =
//    for {
//      accounts <- addresses.traverse(jbokClient.account.getAccount(_))
//      nonces   <- addresses.traverse(jbokClient.account.getEstimatedNonce)
//      newAccounts = accounts.zip(nonces).map {
//        case (account, nonce) => account.copy(nonce = UInt256(nonce))
//      }
//    } yield newAccounts
//
//  private def stxStream: Stream[F, Unit] = {
//    def submitStxsToNetwork: F[Unit] =
//      for {
//        cs <- clients.get
//        client = cs.head
//        accounts  <- getAccounts(keyPairs.map(Address.apply), client)
//        (_, stxs) <- TxGen.genTxs[F](nTx, keyPairs.zip(accounts).toMap)
//        _         <- stxs.traverse(stx => client.transaction.sendTx(stx))
//        _ = log.info(s"send ${nTx} stxs to network.")
//      } yield ()
//
//    Stream
//      .fixedDelay[F](fullNodeConfigs.head.core.mining.period)
//      .evalMap(_ => submitStxsToNetwork)
//      .handleErrorWith { err =>
//        Stream.eval(log.error("", err))
//      }
//      .onFinalize(F.delay(log.info("stx stream stop.")))
//  }
//
//  def run: Stream[F, Unit] =
//    for {
//      jbokClients <- Stream.resource(fullNodeConfigs.traverse(x => JbokClientPlatform.resource[F](new URI(s"http://${x.app.service.host}:${x.app.service.port}"))))
//      _           <- Stream.eval(clients.update(_ ++ jbokClients))
//      _           <- Stream.eval(T.sleep(3.seconds))
//      _           <- stxStream
//    } yield ()
//}
//
//object TestNetTxGen {
//  private def loadKeyPairs[F[_]: Async](paths: List[String]): F[List[KeyPair]] =
//    paths
//      .traverse(path =>
//        for {
//          ksp       <- KeyStorePlatform[F](path)
//          addresses <- ksp.listAccounts
//          keyPairs <- addresses.traverse { address =>
//            ksp.unlockAccount(address, "").map(_.keyPair)
//          }
//        } yield keyPairs)
//      .map(_.flatten)
//
//  private def loadGenessisConfig[F[_]](paths: List[String])(implicit F: Sync[F]): F[GenesisConfig] =
//    for {
//      genesisList <- paths.traverse(path => Config[F].read[GenesisConfig](Paths.get(path)))
//      genesis <- if (genesisList.toSet.size == 1) {
//        genesisList.head.pure[F]
//      } else {
//        F.raiseError(new Exception("nodes incompatible."))
//      }
//    } yield genesis
//
//  private def loadFullNodeConfigs[F[_]: Sync](paths: List[String]): F[List[FullConfig]] =
//    ???
////    paths.traverse(path => Config[F].read[CoreConfig](Paths.get(path)))
//
//  private def parseFileDir[F[_]](implicit F: Sync[F]): F[(List[String], List[String], List[String])] = {
//    val homePath = System.getProperty("user.home")
//    val jbokPath = homePath + "/.jbok"
//    for {
//      dir <- F.delay(File.apply(jbokPath))
//      files: List[File] <- if (!dir.exists || !dir.isDirectory || dir.list.isEmpty) {
//        F.raiseError(new Exception(s"not a jbok home dir: ${jbokPath}."))
//      } else {
//        F.delay(dir.list.toList.sortBy(_.toString))
//      }
//      keyPairPath         = files.map(_ / "keystore").map(_.toString)
//      genesisConfigPath   = files.map(_ / "genesis.json").map(_.toString)
//      fullNodeConfigsPath = files.map(_ / "app.json").map(_.toString)
//    } yield (keyPairPath, genesisConfigPath, fullNodeConfigsPath)
//  }
//
//  def apply[F[_]](nTx: Int = 2)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): F[TestNetTxGen[F]] =
//    for {
//      (keyPairDir, genesisDir, fullNodeDir) <- parseFileDir
//      keyPairs                              <- loadKeyPairs[F](keyPairDir)
//      genesisConfig                         <- loadGenessisConfig[F](genesisDir)
//      fullNodeConfigs                       <- loadFullNodeConfigs[F](fullNodeDir)
//      clients                               <- Ref.of[F, List[JbokClient[F]]](Nil)
//    } yield new TestNetTxGen(clients, genesisConfig, fullNodeConfigs, keyPairs, nTx)
//}
