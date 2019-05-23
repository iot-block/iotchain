package jbok.app

import java.nio.file.Path

import cats.effect._
import cats.effect.concurrent.Ref
import distage._
import doobie.util.transactor.Transactor
import jbok.app.service._
import jbok.app.service.store.doobie.{Doobie, DoobieTransactionStore}
import jbok.app.service.store.{Migration, TransactionStore}
import jbok.common.config.Config
import jbok.core.CoreModule
import jbok.core.api._
import jbok.core.config.CoreConfig
import jbok.core.keystore.Wallet
import jbok.core.models.Address

class AppModule[F[_]: TagK](implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) extends ModuleDef {
  addImplicit[Bracket[F, Throwable]]

  make[Transactor[F]].fromResource((config: CoreConfig) => Doobie.xa[F](config.db))
  make[Unit].fromEffect((config: CoreConfig) => Migration.migrate[F](config.db))
  make[TransactionStore[F]].from[DoobieTransactionStore[F]]
  make[Ref[F, Map[Address, Wallet]]].fromEffect(Ref.of[F, Map[Address, Wallet]](Map.empty))

  make[ServiceHelper[F]]
  make[AccountAPI[F]].from[AccountService[F]]
  make[AdminAPI[F]].from[AdminService[F]]
  make[BlockAPI[F]].from[BlockService[F]]
  make[ContractAPI[F]].from[ContractService[F]]
  make[MinerAPI[F]].from[MinerService[F]]
  make[PersonalAPI[F]].from[PersonalService[F]]
  make[TransactionAPI[F]].from[TransactionService[F]]
  make[HttpService[F]]

  make[FullNode[F]]
}

object AppModule {
  def resource[F[_]: TagK](config: CoreConfig = CoreModule.testConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F](config) ++ new AppModule[F]).toCats

  def resource[F[_]: TagK](path: Path)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Resource.liftF(Config[F].read[CoreConfig](path)).flatMap(config => resource[F](config))
}
