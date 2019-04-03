package jbok.app.service.store

import cats.effect.{ConcurrentEffect, ContextShift, IO, Resource}
import jbok.app.service.store.impl.doobie._
import jbok.app.service.store.impl.quill._
import monix.eval.Task

class ServiceStore[F[_]](
    val blockStore: BlockStore[F],
    val transactionStore: TransactionStore[F]
)

object ServiceStore {
  def doobie(dbUrl: String)(implicit cs: ContextShift[IO]): Resource[IO, ServiceStore[IO]] =
    Doobie.newXa(dbUrl).map { xa =>
      val blockStore       = new DoobieBlockStore(xa)
      val transactionStore = new DoobieTransactionStore(xa)
      new ServiceStore[IO](blockStore, transactionStore)
    }

  def quill(dbUrl: String)(implicit F: ConcurrentEffect[Task]): Resource[IO, ServiceStore[IO]] =
    Quill.newCtx(dbUrl).map { ctx =>
      val blockStore       = new QuillBlockStore(ctx)
      val transactionStore = new QuillTransactionStore(ctx)
      new ServiceStore[IO](blockStore, transactionStore)
    }
}
