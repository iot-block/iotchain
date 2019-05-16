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
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.crypto.signature.KeyPair.Secret

import scala.collection.immutable.ListMap

final case class GenesisBuilder(
    base: GenesisConfig,
    alloc: Map[Address, BigInt] = Map.empty,
    miners: List[Secret] = Nil,
    chainId: BigInt = BigInt(1)
) {
  def withAlloc(address: Address, amount: BigInt): GenesisBuilder =
    copy(alloc = alloc + (address -> amount))

  def withMiner(secret: Secret): GenesisBuilder =
    copy(miners = secret :: miners)

  def withChainId(id: BigInt): GenesisBuilder =
    copy(chainId = id)

  def build: GenesisConfig = {
    val template = base.copy(
      alloc = this.alloc.map { case (k, v) => k.toString -> v.toString(10) },
      chainId = this.chainId,
    )
    val minerAddresses = miners.map { s =>
      val pub = Signature[ECDSA].generatePublicKey[IO](s).unsafeRunSync()
      Address(KeyPair(pub, s))
    }
    Clique.generateGenesisConfig(template, minerAddresses)
  }
}

object GenesisBuilder extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    AppModule
      .resource[IO]()
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
