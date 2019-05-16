package jbok.app

import cats.effect._
import cats.effect.concurrent.Ref
import distage._
import doobie.util.transactor.Transactor
import jbok.app.config.{AppConfig, DatabaseConfig, ServiceConfig}
import jbok.app.service.{AdminService, PersonalService, PublicService}
import jbok.app.service.store.doobie.{Doobie, DoobieBlockStore, DoobieTransactionStore}
import jbok.app.service.store.{BlockStore, Migration, TransactionStore}
import jbok.common.config.Config
import jbok.core.CoreModule
import jbok.core.config.Configs.CoreConfig
import jbok.core.keystore.Wallet
import jbok.core.models.Address
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}

class AppModule[F[_]: TagK](implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) extends ModuleDef {
  addImplicit[Bracket[F, Throwable]]

  make[AppConfig].fromEffect(Config[F].fromResource[AppConfig]("config.app.test.yaml"))
  make[DatabaseConfig].from((config: AppConfig) => config.db)
  make[ServiceConfig].from((config: AppConfig) => config.service)

  make[Transactor[F]].fromResource((config: AppConfig) => Doobie.fromConfig[F](config.db))
  make[Unit].fromEffect((config: AppConfig) => Migration.migrate[F](config.db))
  make[BlockStore[F]].from[DoobieBlockStore[F]]
  make[TransactionStore[F]].from[DoobieTransactionStore[F]]
  make[Ref[F, Map[Address, Wallet]]].fromEffect(Ref.of[F, Map[Address, Wallet]](Map.empty))

  make[PublicAPI[F]].from[PublicService[F]]
  make[PersonalAPI[F]].from[PersonalService[F]]
  make[AdminAPI[F]].from[AdminService[F]]

  make[HttpRpcService[F]]
  make[FullNode[F]]
}

object AppModule {
  def locator[F[_]: TagK](implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F]() ++ new AppModule[F]()).toCats

  def withConfig[F[_]: TagK](config: CoreConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F]().overridenBy(CoreModule.configModule(config)) ++ new AppModule[F]()).toCats
}
