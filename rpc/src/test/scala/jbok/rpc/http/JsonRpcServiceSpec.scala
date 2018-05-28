package jbok.rpc.http

import cats.effect.IO
import io.circe.Json
import io.circe.syntax._
import jbok.JbokSpec
import jbok.rpc.json._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._

class JsonRpcServiceSpec extends JbokSpec {
  "json-rpc service" should {
    def check[A](actual: IO[Response[IO]], expectedStatus: Status, expectedBody: A)(
        implicit ev: EntityDecoder[IO, A]
    ) = {
      val actualResp = actual.unsafeRunSync
      actualResp.status shouldBe expectedStatus
      actualResp.as[A].unsafeRunSync shouldBe expectedBody
    }

    val service = JsonRpcService.service[IO]

    val encoder = JsonRpcMsg.encoder
    implicit val entityEncoder = jsonEncoderOf[IO, JsonRpcMsg]

    def response(msg: JsonRpcMsg): IO[Response[IO]] = {
      import org.http4s.client.dsl.io._
      val req = POST(uri("/jsonrpc"), msg.asJson).unsafeRunSync
      service.orNotFound.run(req)
    }

    "handle json-rpc request" in {
      val request = JsonRpcRequest("subtract", Some(List(42, 23).asJson), RequestId(1))
      check[Json](
        response(request),
        Status.Ok,
        encoder(JsonRpcResponse.methodNotFound("method subtract not found", RequestId(1)))
      )
    }
  }
}
