package jbok.common.log

import cats.effect.Sync
import scala.language.experimental.macros

trait Logger[F[_]] {
  final def t(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def d(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def i(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def w(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def e(message: => String): F[Unit] = macro Macros.autoLevel1[F]

  final def trace(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def debug(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def info(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def warn(message: => String): F[Unit] = macro Macros.autoLevel1[F]
  final def error(message: => String): F[Unit] = macro Macros.autoLevel1[F]

  final def t(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def d(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def i(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def w(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def e(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]

  final def trace(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def debug(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def info(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def warn(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
  final def error(message: => String, t: Throwable): F[Unit] = macro Macros.autoLevel2[F]
}

object Logger {
  def apply[F[_]: Sync]: Logger[F] = new Logger[F] {}
}
