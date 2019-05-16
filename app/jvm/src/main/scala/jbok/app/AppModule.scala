package jbok.app

import java.nio.file.Path

import cats.effect._
import cats.effect.concurrent.Ref
import distage._
import doobie.util.transactor.Transactor
import jbok.app.config.{AppConfig, DatabaseConfig, FullConfig, ServiceConfig}
import jbok.app.service._
import jbok.app.service.store.doobie.{Doobie, DoobieBlockStore, DoobieTransactionStore}
import jbok.app.service.store.{BlockStore, Migration, TransactionStore}
import jbok.common.config.Config
import jbok.core.CoreModule
import jbok.core.api._
import jbok.core.config.CoreConfig
import jbok.core.keystore.Wallet
import jbok.core.models.Address

class AppModule[F[_]: TagK](config: AppConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]) extends ModuleDef {
  addImplicit[Bracket[F, Throwable]]

  make[AppConfig].from(config)
  make[FullConfig].from((core: CoreConfig, app: AppConfig) => FullConfig(core, app))
  make[DatabaseConfig].from((config: AppConfig) => config.db)
  make[ServiceConfig].from((config: AppConfig) => config.service)

  make[Transactor[F]].fromResource((config: AppConfig) => Doobie.fromConfig[F](config.db))
  make[Unit].fromEffect((config: AppConfig) => Migration.migrate[F](config.db))
  make[BlockStore[F]].from[DoobieBlockStore[F]]
  make[TransactionStore[F]].from[DoobieTransactionStore[F]]
  make[Ref[F, Map[Address, Wallet]]].fromEffect(Ref.of[F, Map[Address, Wallet]](Map.empty))

  make[ServiceHelper[F]]
  make[AccountAPI[F]].from[AccountService[F]]
  make[AdminAPI[F]].from[AdminService[F]]
  make[BlockAPI[F]].from[BlockService[F]]
  make[ContractAPI[F]].from[ContractService[F]]
  make[PersonalAPI[F]].from[PersonalService[F]]
  make[TransactionAPI[F]].from[TransactionService[F]]
  make[HttpService[F]]

  make[FullNode[F]]
}

object AppModule {
  val appConfig: AppConfig = Config[IO].readResource[AppConfig]("config.app.test.yaml").unsafeRunSync()

  def resource[F[_]: TagK](config: FullConfig = FullConfig(CoreModule.coreConfig, appConfig))(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F](config.core) ++ new AppModule[F](config.app)).toCats

  def resource[F[_]: TagK](path: Path)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] = {
    Resource.liftF(Config[F].read[FullConfig](path)).flatMap(config =>
      Injector().produceF[F](new CoreModule[F](config.core) ++ new AppModule[F](config.app)).toCats
    )
  }
}
