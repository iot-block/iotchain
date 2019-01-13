package jbok.network.client

import cats.effect.IO
import jbok.network.facade.{Axios, Config, Response}

import scala.scalajs.js

object HttpClient {
  def request(config: Config): IO[Response] =
    IO.fromFuture(IO(Axios.request(config).toFuture))

  def get(url: String): IO[Response] =
    request(new Config(url))

  def post(url: String, _data: String): IO[Response] =
    request(new Config(url) {
      override val method: String = "post"
      override val data: js.Any   = _data
    })
}
