package jbok.sdk

import java.net.URI

import cats.effect.IO
import jbok.common.execution._
import jbok.network.Request
import jbok.network.client.{Client, HttpClient, WsClient}
import jbok.network.rpc.RpcClient

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("SdkClient")
@JSExportAll
class SdkClient(client: RpcClient[IO]) {
  def jsonrpc(json: String): js.Promise[String] =
    (for {
      req  <- Request.fromText[IO](json)
      resp <- client.request(req)
      text <- resp.asJson
    } yield text.noSpaces).timeout(10.seconds).unsafeToFuture().toJSPromise
}

@JSExportTopLevel("Client")
@JSExportAll
object SdkClient {

  private def getJbokClient(uri: URI, client: Client[IO]): IO[RpcClient[IO]] =
    client.start.map(_ => RpcClient(client))

  def ws(url: String): js.Promise[SdkClient] = {
    val uri = new URI(url)
    val client = for {
      client    <- WsClient[IO](uri)
      rpcClient <- getJbokClient(uri, client)
    } yield new SdkClient(rpcClient)

    client.unsafeToFuture().toJSPromise
  }

  def http(url: String): SdkClient = {
    val doReq = (s: String) => HttpClient.post(url, s).map(_.data)
    new SdkClient(RpcClient(doReq))
  }
}
