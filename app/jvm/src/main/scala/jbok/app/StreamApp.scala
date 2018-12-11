package jbok.app

import fs2._
import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}

trait StreamApp extends IOApp {
  def runStream[A](stream: Stream[IO, A]): IO[ExitCode] =
    stream.compile.drain.as(ExitCode.Success)
}
