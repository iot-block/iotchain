package jbok.network.http

import cats.effect.{Async, IO}
import jbok.network.facade.{Axios, Config, Response}

import scala.scalajs.js

object HttpClient {
  def request[F[_]](config: Config)(implicit F: Async[F]): F[Response] =
    F.liftIO(IO.fromFuture(IO(Axios.request(config).toFuture)))

  def get[F[_]](url: String)(implicit F: Async[F]): F[Response] =
    request[F](new Config(url))

  def post[F[_]](url: String, _data: String)(implicit F: Async[F]): F[Response] =
    request[F](new Config(url) {
      override val method: String = "post"
      override val data: js.Any   = _data
    })
}
