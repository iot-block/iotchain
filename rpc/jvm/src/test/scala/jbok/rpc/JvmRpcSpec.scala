package jbok.rpc

import cats.effect.IO
import jbok.rpc.client.Client
import jbok.rpc.execution._
import jbok.rpc.server.Server
import jbok.rpc.transport.WSTransport
import org.scalatest.{Matchers, WordSpec}

class JvmRpcSpec extends WordSpec with Matchers {
  val api = new TestAPI {}
  val addr = HostPort("localhost", 9033)
  val service = JsonRPCService().mountAPI[TestAPI](api)
  val server = Server[IO](addr, service.handle).unsafeRunSync()
  val transport = WSTransport[IO](addr).unsafeRunSync()
  val client = Client[IO](transport)
  server.start.unsafeRunSync()

  "jvm rpc" should {
    "mount and use API" in {
      val resp = for {
        _ <- server.start
        _ <- client.start
        c = client.useAPI[TestAPI]
        resp <- c.grow(21, "taylor")
        _ <- client.stop
        _ <- server.stop
      } yield resp

      resp.unsafeRunSync() shouldBe api.grow(21, "taylor").unsafeRunSync()
    }
  }
}
