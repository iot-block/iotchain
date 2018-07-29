//package jbok.network
//
//import cats.effect.IO
//import fs2._
//import fs2.async.mutable.Topic
//import jbok.network.json.JsonRPCMessage.RequestId
//import org.scalatest.{Matchers, WordSpec}
//
//import scala.concurrent.ExecutionContext.Implicits.global
//
//class RPCSpec extends WordSpec with Matchers {
//  "json rpc macro" should {
//    val apiImpl = TestAPI.apiImpl
//    val service = JsonRPCService[IO]
//    val server = service.mountAPI[TestAPI[IO]](apiImpl)
//
//    "generate client code" in {
//      val client: JsonRPCClient[IO] = new JsonRPCClient[IO] {
//        override def request(id: RequestId, json: String): IO[String] = {
//          server.handle(json)
//        }
//
//        override def getOrCreateTopic(method: String): IO[Topic[IO, Option[String]]] =
//          fs2.async.topic[IO, Option[String]](None)
//
//        override def start: IO[Unit] = IO.unit
//
//        override def stop: IO[Unit] = IO.unit
//      }
//      val c = client.useAPI[TestAPI[IO]]
//
//      c.foo.attempt.unsafeRunSync() shouldBe Right(42)
//      c.bar.attempt.unsafeRunSync() shouldBe Right("oho")
//      c.grow(21, "aha").attempt.unsafeRunSync() shouldBe Right(Person(22, "aha"))
//    }
//
//    "generate server code" in {
//      server.handlers.size shouldBe 3
//    }
//  }
//}
