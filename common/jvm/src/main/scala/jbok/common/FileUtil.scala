package jbok.common

import java.io.InputStream
import java.nio.channels.{FileLock, OverlappingFileLockException}
import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.{Path, StandardOpenOption}

import better.files.File
import cats.effect.{Resource, Sync}
import cats.implicits._
import jbok.common.log.Logger

trait FileUtil[F[_]] {
  def read(path: Path): F[String]

  def readResource(path: String): F[String]

  def open(path: Path, create: Boolean = true, asDirectory: Boolean = false): F[File]

  def inputStream(path: Path): Resource[F, InputStream]

  def dump(text: String, path: Path, create: Boolean = true): F[Unit]

  def append(text: String, path: Path, create: Boolean = true): F[Unit]

  def lock(path: Path, content: String = ""): Resource[F, FileLock]

  def remove(path: Path): F[Unit]

  def temporaryFile(prefix: String = "", suffix: String = ""): Resource[F, File]

  def temporaryDir(prefix: String = ""): Resource[F, File]
}

object FileUtil {
  implicit val defaultCharset: Charset = StandardCharsets.UTF_8

  final case class FileLockErr(path: Path) extends Exception(s"path=${path.toAbsolutePath} is already locked")

  def apply[F[_]](implicit ev: FileUtil[F]): FileUtil[F] = ev

  implicit def instance[F[_]](implicit F: Sync[F]): FileUtil[F] = new FileUtil[F] {
    private[this] val log = Logger[F]

    override def read(path: Path): F[String] =
      log.i(s"reading text from path=${path.toAbsolutePath}") >>
        F.delay(File(path).lines.mkString("\n"))

    override def readResource(path: String) =
      log.i(s"reading text from resource=${path}") >>
        F.delay(better.files.Resource.getAsString(path))

    override def open(path: Path, create: Boolean, asDirectory: Boolean): F[File] =
      if (create) {
        F.delay(File(path).createIfNotExists(createParents = true, asDirectory = asDirectory))
      } else {
        F.delay(File(path))
      }

    override def inputStream(path: Path): Resource[F, InputStream] =
      Resource {
        for {
          file <- open(path, create = false)
          is   <- F.delay(file.newInputStream)
        } yield is -> F.delay(is.close())
      }

    override def dump(text: String, path: Path, create: Boolean): F[Unit] =
      log.i(s"writing text to path=${path.toAbsolutePath}") >>
        open(path, create, asDirectory = false).flatMap(file => F.delay(file.overwrite(text)).void)

    override def append(text: String, path: Path, create: Boolean): F[Unit] =
      log.i(s"append text to path=${path.toAbsolutePath}") >>
        open(path, create, asDirectory = false).flatMap(file => F.delay(file.append(text)).void)

    override def lock(path: Path, content: String): Resource[F, FileLock] =
      Resource
        .make[F, FileLock] {
          for {
            _       <- log.i(s"acquiring file lock at path=${path.toAbsolutePath}")
            file    <- F.delay(File(path).createIfNotExists(createParents = true))
            channel <- F.delay(file.newFileChannel(StandardOpenOption.WRITE :: Nil))
            lock <- F.delay(channel.tryLock()).adaptError {
              case _: OverlappingFileLockException => FileLockErr(path)
            }
            _ <- if (lock == null) F.raiseError(FileLockErr(path)) else F.unit
          } yield lock
        } { lock =>
          F.delay(File(path).delete(swallowIOExceptions = true)) >> F.delay(lock.release()) >>
            log.i(s"released file lock at path=${path.toAbsolutePath}")
        }

    override def remove(path: Path): F[Unit] =
      log.i(s"removing file at path=${path.toAbsolutePath}") >> F.delay(File(path).delete()).void

    override def temporaryFile(prefix: String, suffix: String): Resource[F, File] = Resource {
      for {
        file <- F.delay(File.newTemporaryFile(prefix, suffix))
        _    <- log.i(s"creating temporary file path=${file.path}")
      } yield file -> (F.delay(file.delete()) >> log.i(s"deleted temporary file path=${file.path}"))
    }

    override def temporaryDir(prefix: String): Resource[F, File] = Resource {
      for {
        file <- F.delay(File.newTemporaryDirectory(prefix))
        _    <- log.i(s"creating temporary dir path=${file.path}")
      } yield file -> (F.delay(file.delete()) >> log.i(s"deleted temporary dir path=${file.path}"))
    }
  }
}
