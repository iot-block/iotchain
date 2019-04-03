package jbok.common

import java.nio.channels.OverlappingFileLockException
import java.nio.file.{Path, StandardOpenOption}

import better.files.File
import cats.effect.{IO, Resource}
import cats.implicits._
import jbok.common.log.{Log, Logger}

trait FileUtil[F[_]] {
  def read(path: Path): F[String]

  def dump(text: String, path: Path, create: Boolean = true): F[Unit]

  def lock(path: Path, content: String = ""): Resource[F, Unit]

  def remove(path: Path): F[Unit]
}

object FileUtil extends FileUtil[IO] {
  private[this] val log = Logger[IO]
  final case class FileLockErr(path: Path) extends Exception(s"path=${path} is already locked")

  override def read(path: Path): IO[String] =
    Log.i(s"reading text from path=${path}") >>
      IO(File(path).lines.mkString("\n"))

  override def dump(text: String, path: Path, create: Boolean): IO[Unit] =
    Log.i(s"writing text to path=${path}")

  override def lock(path: Path, content: String): Resource[IO, Unit] =
    Resource.make {
      for {
        _       <- log.i(s"acquiring file lock at path=${path}")
        file    <- IO(File(path).createIfNotExists(createParents = true))
        channel <- IO(file.newFileChannel(StandardOpenOption.WRITE :: Nil))
        lock <- IO(channel.tryLock()).adaptError {
          case _: OverlappingFileLockException => FileLockErr(path)
        }
        _ <- if (lock == null) IO.raiseError(FileLockErr(path)) else IO.unit
      } yield lock
    } { lock =>
      IO(File(path).delete(swallowIOExceptions = true)) >> IO(lock.release()) >>
        Log.i(s"released file lock at path=${path}")
    }.void

  override def remove(path: Path): IO[Unit] =
    IO(File(path).delete()).void
}
