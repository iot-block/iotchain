package jbok.network.client

import jbok.JbokAsyncSpec

import scala.concurrent.ExecutionContext
import jbok.common.execution._

class HttpClientSpec extends JbokAsyncSpec {
  implicit override def executionContext: ExecutionContext = EC

  "HttpClient" should {
    "get url" in {
      for {
        resp <- HttpClient.get("http://www.baidu.com")
        _ = println(resp.status)
        _ = println(resp.statusText)
      } yield ()
    }
  }
}
