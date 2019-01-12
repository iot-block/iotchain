package jbok.sdk

import jbok.JbokAsyncSpec

import scala.concurrent.ExecutionContext
import jbok.common.execution._

class SdkClientSpec extends JbokAsyncSpec {

  implicit override def executionContext: ExecutionContext = EC

  "SdkClient" should {
    "make http request" in {
      val url = "http://localhost:20002"
      val data =
        """{"jsonrpc":"2.0","id":"81ec047e-daa4-4b98-af58-aea736722e66","method":"getAccount","params":["928e878a6eb914e6999f1c88ebfa3cf017eef6e5",{"Latest":{}}]}"""
      val client = SdkClient.http(url)
      client.jsonrpc(data).toFuture.map { result =>
        println(result)
        result.length > 0 shouldBe true
      }(EC)
    }
  }
}
