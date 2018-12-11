package jbok.common

import java.nio.channels.OverlappingFileLockException
import java.nio.file.{Path, StandardOpenOption}

import better.files.File
import better.files.File.RandomAccessMode
import cats.effect.{Resource, Sync}
import cats.implicits._

case class FileLockErr(path: Path) extends Exception(s"${path} is already locked")

object FileLock {
  private[this] val log = org.log4s.getLogger("FileLock")

  def lock[F[_]](path: Path)(implicit F: Sync[F]): Resource[F, Unit] =
    Resource
      .make {
        for {
          _ <- F.delay(log.info(s"acquiring file lock at ${path}"))
          file <- F.delay(
            File(path).createIfNotExists(createParents = true).newRandomAccess(RandomAccessMode.readWrite))
          channel <- F.delay(file.getChannel)
          lock <- F.delay(channel.tryLock()).adaptError {
            case _: OverlappingFileLockException => FileLockErr(path)
          }
          _ <- if (lock == null) F.raiseError(FileLockErr(path)) else F.unit
        } yield lock
      } { lock =>
        F.delay(File(path).delete(swallowIOExceptions = true)) >> F.delay(lock.release()) <* F.delay(
          log.info(s"released file lock at ${path}"))
      }
      .as(())
}
