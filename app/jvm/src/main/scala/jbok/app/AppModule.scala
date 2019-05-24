package jbok.app

import java.nio.file.Path

import cats.effect._
import distage._
import doobie.util.transactor.Transactor
import jbok.app.service._
import jbok.app.service.store.doobie.{Doobie, DoobieBlockStore, DoobieTransactionStore}
import jbok.app.service.store.{BlockStore, Migration, TransactionStore}
import jbok.common.config.Config
import jbok.core.CoreModule
import jbok.core.api._
import jbok.core.config.FullConfig

class AppModule[F[_]: TagK](implicit F: ConcurrentEffect[F], cs: ContextShift[F]) extends ModuleDef {
  addImplicit[Bracket[F, Throwable]]

  make[Transactor[F]].fromResource((config: FullConfig) => Doobie.xa[F](config.db))
  make[Unit].fromEffect((config: FullConfig) => Migration.migrate[F](config.db))
  make[TransactionStore[F]].from[DoobieTransactionStore[F]]
  make[BlockStore[F]].from[DoobieBlockStore[F]]

  make[ServiceHelper[F]]
  make[AccountAPI[F]].from[AccountService[F]]
  make[AdminAPI[F]].from[AdminService[F]]
  make[BlockAPI[F]].from[BlockService[F]]
  make[ContractAPI[F]].from[ContractService[F]]
  make[MinerAPI[F]].from[MinerService[F]]
  make[PersonalAPI[F]].from[PersonalService[F]]
  make[TransactionAPI[F]].from[TransactionService[F]]
  make[HttpService[F]]

  make[StoreUpdateService[F]]
  make[FullNode[F]]
}

object AppModule {
  def resource[F[_]: TagK](config: FullConfig = CoreModule.testConfig)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Injector().produceF[F](new CoreModule[F](config) ++ new AppModule[F]).toCats

  def resource[F[_]: TagK](path: Path)(implicit F: ConcurrentEffect[F], cs: ContextShift[F], T: Timer[F]): Resource[F, Locator] =
    Resource.liftF(Config[F].read[FullConfig](path)).flatMap(config => resource[F](config))
}
