package jbok.app.txgen

import cats.effect._
import cats.implicits._
import fs2._
import jbok.common.log.Logger
import jbok.core.api.JbokClient
import jbok.core.config.FullConfig
import jbok.core.mining.TxGen

final class TxGenerator[F[_]](config: FullConfig, txGen: TxGen[F], client: JbokClient[F])(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

  def submitTransactions(n: Int = 10): F[Unit] =
    for {
      txs <- txGen.genValidExternalTxN(n)
      _   <- txs.traverse(stx => client.transaction.sendTx(stx))
      _   <- log.info(s"send ${n} transactions to network")
    } yield ()

  val stream: Stream[F, Unit] =
    Stream.eval(log.i(s"starting TxGen")) ++
      Stream
        .repeatEval(submitTransactions())
        .metered(config.mining.period)
}
