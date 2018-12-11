package jbok.common

import java.nio.file.{Path, StandardOpenOption}

import better.files.File
import cats.effect.{Resource, Sync}
import cats.implicits._

object FileLock {
  private[this] val log = org.log4s.getLogger("FileLock")

  def lock[F[_]](path: Path)(implicit F: Sync[F]): Resource[F, Unit] =
    Resource
      .make {
        for {
          file    <- F.delay(File(path).createIfNotExists(createParents = true))
          channel <- F.delay(file.newFileChannel(StandardOpenOption.WRITE :: Nil))
          lock    <- F.delay(channel.tryLock())
          _       <- F.delay(log.debug(s"acquired file lock at ${path}"))
        } yield lock
      } { lock =>
        F.delay(log.debug(s"releasing file lock at ${path}")) >>
          F.delay(File(path).delete()) >>
          F.delay(lock.release())
      }
      .as(())
}
