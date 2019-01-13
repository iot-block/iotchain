package jbok.network.client

import jbok.JbokAsyncSpec

import scala.concurrent.ExecutionContext
import jbok.common.execution._
import jbok.network.facade.Config

class HttpClientSpec extends JbokAsyncSpec {
  implicit override def executionContext: ExecutionContext = EC

  "HttpClient" should {
    "get" in {
      for {
        resp <- HttpClient.get("http://www.baidu.com")
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
        resp <- HttpClient.request(config)
        _ = println(resp.status)
        _ = println(resp.statusText)
        _ = println(resp.data.length)
      } yield ()
    }
  }
}
