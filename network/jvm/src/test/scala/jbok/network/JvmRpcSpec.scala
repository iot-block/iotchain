package jbok.network

import cats.effect.IO
import cats.implicits._
import fs2._
import jbok.network.client.Client
import jbok.network.server.Server
import jbok.network.transport.WSTransport
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import jbok.network.execution._

class JvmRpcSpec extends WordSpec with BeforeAndAfterAll with Matchers {
  val backend = new TestApiImpl[IO](fs2.async.topic[IO, Option[String]](None).unsafeRunSync())
  val addr = NetAddress("localhost", 9040)
  val service = JsonRPCService[IO].mountAPI[TestAPI[IO]](backend)
  val server = Server[IO](addr, service).unsafeRunSync()
  val transport = WSTransport[IO](addr).unsafeRunSync()
  val client = Client[IO](transport)
  val api = client.useAPI[TestAPI[IO]]

  server.start.unsafeRunSync()
  client.start.unsafeRunSync()

  "jvm rpc" should {
    "mount and use API" in {
      val resp = api.grow(21, "taylor").unsafeRunSync()
      resp shouldBe backend.grow(21, "taylor").unsafeRunSync()
    }

    "support sse" in {
      val s = for {
        s <- api.events
          .subscribe(1)
          .unNone
          .take(10)
          .mergeHaltR(Stream.repeatEval(backend.events.publish1("oho".some)).take(10))
      } yield s

      s.compile.toList.unsafeRunSync() should have size 10
    }
  }

  override protected def afterAll(): Unit = {
    client.stop.unsafeRunSync()
    server.stop.unsafeRunSync()
  }
}
