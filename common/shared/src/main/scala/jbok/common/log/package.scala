package jbok.common

import cats.effect.Sync

package object log {
  def getLogger(name: String) = ScribeLog.getLogger(name)

  def setRootLevel[F[_]: Sync](level: Level): F[Unit] =
    ScribeLog.setLevel[F](level)

  def setRootLevel[F[_]: Sync](levelName: String): F[Unit] =
    ScribeLog.setLevel[F](Level.fromName(levelName))
}
