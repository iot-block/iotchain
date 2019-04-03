package jbok.common.config

import java.nio.file.Path

import better.files.File
import cats.effect.IO
import io.circe
import io.circe.{Decoder, Json}
import jbok.common.FileUtil

trait Config[F[_]] {
  def read[A: Decoder](path: Path): F[A]

  def dump(json: Json, path: Path): F[Unit]
}

object Config extends Config[IO] {
  override def read[A: Decoder](path: Path): IO[A] = File(path).extension match {
    case Some("json") =>
      FileUtil.read(path).flatMap(text => IO.fromEither(circe.parser.decode[A](text)))
    case Some("yaml") =>
      FileUtil.read(path).flatMap(text => IO.fromEither(circe.yaml.parser.parse(text).flatMap(_.as[A])))
    case Some(_) =>
      IO.raiseError(new Exception(s"Unknown extension path=${path}"))
    case None =>
      IO.raiseError(new Exception(s"No extension path=${path}"))
  }

  override def dump(json: Json, path: Path): IO[Unit] = File(path).extension match {
    case Some("json") =>
      FileUtil.dump(json.spaces2, path)
    case Some("yaml") =>
      FileUtil.dump(circe.yaml.Printer.spaces2.pretty(json), path)
    case Some(_) =>
      IO.raiseError(new Exception(s"Unknown extension path=${path}"))
    case None =>
      IO.raiseError(new Exception(s"No extension path=${path}"))
  }
}
