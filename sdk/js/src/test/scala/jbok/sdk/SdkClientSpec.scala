package jbok.sdk

import jbok.JbokAsyncSpec

import scala.concurrent.ExecutionContext
import jbok.common.execution._

class SdkClientSpec extends JbokAsyncSpec {

  implicit override def executionContext: ExecutionContext = EC

  "SdkClient" should {
    "make http request" ignore {
      val url    = "http://localhost:20002"
      val client = SdkClient.http(url)
      client
        .jsonrpc("getAccount", """["928e878a6eb914e6999f1c88ebfa3cf017eef6e5",{"Latest":{}}]""")
        .toFuture
        .map { result =>
          println(result)
          result.length > 0 shouldBe true
        }(EC)
    }
  }
}
