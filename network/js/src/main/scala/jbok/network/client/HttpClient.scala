package jbok.network.client

import cats.effect.IO
import jbok.network.facade.{Axios, Response}

object HttpClient {
  def get(url: String): IO[Response] =
    IO.fromFuture(IO(Axios.get(url).toFuture))
}
