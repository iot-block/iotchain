package jbok.sdk

import java.net.URI

import cats.effect.{Clock, IO}
import io.circe.Json
import io.circe.parser._
import jbok.network.http.HttpTransport
import jbok.network.rpc.{RpcClient, RpcRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.JSON

@JSExportAll
final class SdkClient(val uri: URI, val client: RpcClient[IO, Json]) {
  def fetch(api: String, method: String, params: js.UndefOr[js.Any]): Promise[String] = {
    val json = params.toOption match {
      case Some(a) => parse(JSON.stringify(a)).getOrElse(Json.Null)
      case None => Json.Null
    }
    val request = RpcRequest(List(api, method), json)
    client.transport.fetch(request).map(_.spaces2).unsafeToFuture().toJSPromise
  }
}

@JSExportTopLevel("SdkClient")
@JSExportAll
object SdkClient {
  implicit val clock: Clock[IO] = Clock.create[IO]

  def http(url: String): SdkClient = {
    val transport = HttpTransport[IO](url)
    val client    = RpcClient(transport)
    new SdkClient(new URI(url), client)
  }
}
