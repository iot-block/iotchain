package jbok.network.client

import cats.effect.IO
import jbok.network.facade.{Axios, Response}

object HttpClient {
  def get(url: String): IO[Response] =
    IO.fromFuture(IO(Axios.get(url).toFuture))

  def request(url: String, data: String): IO[String] = {
    IO.fromFuture(IO(Axios.post(url, data).toFuture)).map { resp =>
     scalajs.js.JSON.stringify(resp.data)
    }
  }
}
