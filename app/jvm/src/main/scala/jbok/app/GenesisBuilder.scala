package jbok.app

import java.nio.file.Paths

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import distage.Injector
import io.circe.syntax._
import jbok.common.config.Config
import jbok.core.config.GenesisConfig
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStore

import scala.collection.immutable.ListMap

object GenesisBuilder extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    Injector()
      .produceF[IO](new AppModule[IO])
      .use { locator =>
        val keystore = locator.get[KeyStore[IO]]
        val clique   = locator.get[Clique[IO]]
        for {
          address <- keystore.listAccounts.flatMap(
            _.headOption.fold(keystore.readPassphrase("please input your passphrase>") >>= keystore.newAccount)(IO.pure)
          )
          _ <- keystore.readPassphrase(s"unlock address ${address}>").flatMap(p => keystore.unlockAccount(address, p))
          signers = List(address)
          genesis = Clique.generateGenesisConfig(GenesisConfig.generate(0, ListMap.empty), signers)
          _ <- Config[IO].dump(genesis.asJson, Paths.get(""))
//          _ <- IO(File(CoreConfig.reference.genesisPath).createIfNotExists().overwrite(genesis.asJson.spaces2))
        } yield ExitCode.Success
      }
}
