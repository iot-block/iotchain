package jbok.app.service

import cats.data.OptionT
import cats.effect.{Sync, Timer}
import cats.implicits._
import fs2._
import jbok.app.service.store.{BlockStore, TransactionStore}
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.core.ledger.History
import spire.compat._

import scala.concurrent.duration._

final class StoreUpdateService[F[_]](history: History[F], blockStore: BlockStore[F], txStore: TransactionStore[F])(implicit F: Sync[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  def findForkPoint(start: N): F[N] =
    for {
      hash1 <- blockStore.getBlockHashByNumber(start)
      hash2 <- history.getHashByBlockNumber(start)
      number <- (hash1, hash2) match {
        case (Some(h1), Some(h2)) if h1 == h2 => F.pure(start)
        case (Some(_), Some(_))               => findForkPoint(start - 1)
        case _                                => F.raiseError(new Exception(s"fatal error"))
      }
    } yield number

  private def delRange(start: N, end: N): F[Unit] =
    List.range(start, end + 1).traverse_ { number =>
      blockStore.delByBlockNumber(number) >> txStore.delByBlockNumber(number)
    }

  private def syncRange(start: N, end: N): F[Unit] =
    List.range(start, end + 1).traverse_ { number =>
      syncBlock(number) >> syncTransactions(number)
    }

  private def syncBlock(number: N): F[Unit] =
    for {
      header <- history.getBlockHeaderByNumber(number)
      _      <- header.fold(F.unit)(header => blockStore.insert(header.number, header.hash))
    } yield ()

  private def syncTransactions(number: N): F[Unit] =
    (for {
      hash     <- OptionT(history.getHashByBlockNumber(number))
      block    <- OptionT(history.getBlockByHash(hash))
      receipts <- OptionT(history.getReceiptsByHash(hash))
      _        <- OptionT.liftF(txStore.insertBlockTransactions(block, receipts))
    } yield ()).value.void

  def sync: F[Unit] =
    for {
      currentOpt <- blockStore.getBestBlockNumber
      fork       <- currentOpt.fold(N(0).pure[F])(current => findForkPoint(current))
      best       <- history.getBestBlockNumber
      _          <- log.i(s"current: ${fork}, best: ${best}")
      _ <- if (fork == best) {
        F.unit
      } else {
        delRange(fork, best) >> syncRange(fork, best)
      }
    } yield ()

  val stream: Stream[F, Unit] =
    Stream.eval(log.i(s"starting App/StoreUpdateService")) ++
      Stream.repeatEval(sync).metered(10.seconds)
}
