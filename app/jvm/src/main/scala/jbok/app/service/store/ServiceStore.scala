package jbok.app.service.store

import cats.effect.{ContextShift, IO, Resource}
import jbok.app.service.store.impl.doobie._
import jbok.app.service.store.impl.quill._

class ServiceStore[F[_]](
    val blockStore: BlockStore[F],
    val transactionStore: TransactionStore[F]
)

object ServiceStore {
  def doobie(dbPath: Option[String])(implicit cs: ContextShift[IO]): Resource[IO, ServiceStore[IO]] =
    Doobie.newXa(dbPath).map { xa =>
      val blockStore       = new DoobieBlockStore(xa)
      val transactionStore = new DoobieTransactionStore(xa)
      new ServiceStore[IO](blockStore, transactionStore)
    }

  def quill(dbPath: Option[String]): Resource[IO, ServiceStore[IO]] =
    Quill.newCtx(dbPath).map { ctx =>
      val blockStore       = new QuillBlockStore(ctx)
      val transactionStore = new QuillTransactionStore(ctx)
      new ServiceStore[IO](blockStore, transactionStore)
    }
}
