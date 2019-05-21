package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._
import jbok.core.consensus.poa.clique.Clique
import jbok.core.keystore.KeyStore

object GenesisBuilderMain extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    AppModule
      .resource[IO]()
      .use { objects =>
        val keystore = objects.get[KeyStore[IO]]
        val clique   = objects.get[Clique[IO]]
        for {
          address <- keystore.listAccounts.flatMap(
            _.headOption.fold(keystore.readPassphrase("please input your passphrase>") >>= keystore.newAccount)(IO.pure)
          )
          _ <- keystore.readPassphrase(s"unlock address ${address}>").flatMap(p => keystore.unlockAccount(address, p))
          signers = List(address)
//          genesis = Clique.generateGenesisConfig(GenesisConfig.generate(0, ListMap.empty), signers)
//          _ <- Config[IO].dump(genesis.asJson, Paths.get(""))
//          _ <- IO(File(CoreConfig.reference.genesisPath).createIfNotExists().overwrite(genesis.asJson.spaces2))
        } yield ExitCode.Success
      }
}
