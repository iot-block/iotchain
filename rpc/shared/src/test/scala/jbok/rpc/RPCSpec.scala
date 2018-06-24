package jbok.rpc

import cats.effect.IO
import fs2._
import jbok.rpc.json.JsonRPCMessage.RequestId
import org.scalatest.{Matchers, WordSpec}

import scala.language.higherKinds

class RPCSpec extends WordSpec with Matchers {
  "json rpc macro" should {
    val apiImpl = new TestAPI {
      override def foo: IO[Int] = IO(42)

      override def bar: IO[String] = IO("oho")

      override def grow(age: Int, name: String): IO[Person] = IO(Person(age + 1, name))
    }

    val server = JsonRPCService().mountAPI[TestAPI](apiImpl)

    "generate client code" in {
      val client: JsonRPCClient[IO] = new JsonRPCClient[IO] {
        override def request(id: RequestId, json: String): IO[String] = {
          server.handle(json)
        }

        override def subscribe(maxQueued: Int): fs2.Stream[IO, String] = Stream.empty.covary[IO]

        override def start: IO[Unit] = IO.unit

        override def stop: IO[Unit] = IO.unit
      }
      val c = client.useAPI[TestAPI]

      c.foo.attempt.unsafeRunSync() shouldBe Right(42)
      c.bar.attempt.unsafeRunSync() shouldBe Right("oho")
      c.grow(21, "aha").attempt.unsafeRunSync() shouldBe Right(Person(22, "aha"))
    }

    "generate server code" in {
      server.handlers.size shouldBe 3
    }
  }
}
