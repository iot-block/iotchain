package jbok.common

import cats.effect.Sync
import ch.qos.logback.classic.{Level, Logger}
import org.slf4j.LoggerFactory

object logger {

  def setRootLevel[F[_]: Sync](level: String): F[Unit] = Sync[F].delay {
    LoggerFactory
      .getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
      .asInstanceOf[Logger]
      .setLevel(Level.valueOf(level))
  }
}
