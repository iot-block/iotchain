package jbok.app

import cats.effect.{ExitCode, IO, IOApp}
import jbok.app.txgen.TestNetTxGen

object TxGenerator extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    for {
      nTx  <- IO(args.tail.headOption.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(2))
      txtg <- TestNetTxGen[IO](nTx)
      _    <- txtg.run.compile.drain
    } yield ExitCode.Success
}
