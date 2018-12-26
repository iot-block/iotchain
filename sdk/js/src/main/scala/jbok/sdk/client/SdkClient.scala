package jbok.sdk.client

import java.net.URI
import java.util.UUID

import cats.effect.IO
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.network.client.{Client, WsClientNode}
import jbok.network.rpc.RpcClient

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("SdkClient")
@JSExportAll
class SdkClient(client: RpcClient[IO]) {
  def jsonrpc(json: String, id: String = UUID.randomUUID().toString): js.Promise[String] =
    client.jsonrpc(json, id).unsafeToFuture().toJSPromise
}

@JSExportTopLevel("Client")
@JSExportAll
object SdkClient {
  import jbok.network.rpc.RpcServer._

  private def getJbokClient(uri: URI, client: Client[IO, String]): IO[RpcClient[IO]] =
    client.start.map(_ => RpcClient(client))

  def ws(url: String): js.Promise[SdkClient] = {
    val uri = new URI(url)
    val client = for {
      client    <- WsClientNode[IO, String](uri)
      rpcClient <- getJbokClient(uri, client)
    } yield new SdkClient(rpcClient)

    client.unsafeToFuture().toJSPromise
  }
}
