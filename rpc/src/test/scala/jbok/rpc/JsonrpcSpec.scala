package jbok.rpc

import java.net.InetSocketAddress

import cats.effect.IO
import io.circe.syntax._
import jbok.JbokSpec
import jbok.rpc.json._
import org.http4s._

import scala.concurrent.ExecutionContext.Implicits.global

class JsonrpcSpec extends JbokSpec {
  "json-rpc service" should {
    val addr = new InetSocketAddress("localhost", 10000)
    val uri = Uri.unsafeFromString("http://localhost:10000")
    val server = RpcServer[IO](addr).server.unsafeRunSync()
    val client = RpcClient[IO](uri).unsafeRunSync()

    "handle json-rpc request" in {
      client.call("subtract", Some(List(42, 42).asJson)).unsafeRunSync() shouldBe
        JsonrpcResponse.methodNotFound("method subtract not found", RequestId(client.nextId.get - 1))
    }
  }
}
