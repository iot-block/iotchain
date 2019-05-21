package jbok.network.http

import cats.effect.IO
import jbok.common.CommonSpec
import jbok.network.facade.Config

class HttpClientSpec extends CommonSpec {

  "HttpClient" should {
    "get" in {
      for {
        resp <- HttpClient.get[IO]("http://www.baidu.com")
        _ = println(resp.status)
        _ = println(resp.statusText)
        _ = println(resp.data.length)
      } yield ()
    }

    "request" in {
      val config = new Config("http://www.baidu.com") {
        override val responseType: String = "text"
      }

      for {
        resp <- HttpClient.request[IO](config)
        _ = println(resp.status)
        _ = println(resp.statusText)
        _ = println(resp.data.length)
      } yield ()
    }

    "post" in {
      for {
        resp <- HttpClient.post[IO]("https://127.0.0.2:30315", "")
        _ = println(resp.status)
      } yield ()
    }
  }
}
