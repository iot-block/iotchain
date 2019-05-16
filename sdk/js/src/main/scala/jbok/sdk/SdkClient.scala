package jbok.sdk

import java.net.URI
import java.util.UUID

import _root_.io.circe.parser._
import cats.effect.IO
import cats.implicits._
import io.circe.Json
import jbok.network.Request
import jbok.network.rpc.RpcClient

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

//@JSExportTopLevel("SdkClient")
//@JSExportAll
//final class SdkClient(client: RpcClient[IO, Json]) {
//  val admin = client.use[AdminAPI[IO]]
//  val personal = client.use[PersonalAPI[IO]]
//  val public = client.use[PublicAPI[IO]]
////  def jsonrpc(method: String, body: String, id: UUID = UUID.randomUUID()): js.Promise[String] =
////    (for {
////      req  <- Request.json[IO](id, method, parse(body).getOrElse(Json.Null)).pure[IO]
////      resp <- client.request(req)
////      text <- resp.asJson
////    } yield text.noSpaces).timeout(10.seconds).unsafeToFuture().toJSPromise
//}
//
//@JSExportTopLevel("Client")
//@JSExportAll
//object SdkClient {
////  private def getJbokClient(uri: URI, client: Client[IO]): IO[RpcClient[IO]] =
////    client.start.map(_ => RpcClient(client))
////
////  def http(url: String): SdkClient = {
////    val doReq = (s: String) => HttpClient.post(url, s).map(_.data)
////    new SdkClient(RpcClient(doReq))
////  }
//}
