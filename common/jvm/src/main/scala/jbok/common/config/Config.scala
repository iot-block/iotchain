package jbok.common.config

import java.nio.file.Path

import better.files.File
import cats.effect.Sync
import cats.implicits._
import io.circe
import io.circe.{Decoder, Json}
import jbok.common.FileUtil
import jbok.common.log.Logger

trait Config[F[_]] {
  def read[A: Decoder](path: Path): F[A]

  def read[A: Decoder](text: String): F[A]

  def readResource[A: Decoder](name: String): F[A]

  def dump(json: Json, path: Path): F[Unit]
}

object Config {
  def apply[F[_]](implicit ev: Config[F]): Config[F] = ev

  implicit def instance[F[_]](implicit F: Sync[F]): Config[F] = new Config[F] {
    private val log = Logger[F]

    override def read[A: Decoder](path: Path): F[A] =
      log.i(s"reading config from path=${path}") >>
        (File(path).extension match {
          case Some(".json") =>
            FileUtil[F].read(path).flatMap(text => F.fromEither(circe.parser.decode[A](text)))
          case Some(".yml") | Some(".yaml") =>
            FileUtil[F].read(path).flatMap(text => F.fromEither(circe.yaml.parser.parse(text).flatMap(_.as[A])))
          case Some(ext) =>
            F.raiseError(new Exception(s"Unknown extension path=${path},ext=${ext}"))
          case None =>
            F.raiseError(new Exception(s"No extension path=${path}"))
        })

    override def read[A: Decoder](text: String): F[A] =
      log.i(s"parsing config from text") >>
        F.fromEither(circe.yaml.parser.parse(text).flatMap(_.as[A]))

    override def readResource[A: Decoder](name: String): F[A] =
      log.i(s"reading config from resource=${name}") >>
        FileUtil[F].readResource(name) >>= read[A]

    override def dump(json: Json, path: Path): F[Unit] =
      log.i(s"dumping config to path=${path}") >>
        (File(path).extension match {
          case Some(".json") =>
            FileUtil[F].dump(json.spaces2, path)
          case Some(".yml") | Some(".yaml") =>
            FileUtil[F].dump(circe.yaml.Printer.spaces2.copy(preserveOrder = true, indicatorIndent = 2).pretty(json), path)
          case Some(ext) =>
            F.raiseError(new Exception(s"Unknown extension path=${path},ext=${ext}"))
          case None =>
            F.raiseError(new Exception(s"No extension path=${path}"))
        })
  }
}
