package jbok.network

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.network.server.Server
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}
import jbok.network.execution._

class JvmServerSpec extends JbokSpec {
  val addr = NetAddress("localhost", 10001)
  val backend = new TestApiImpl[IO](fs2.async.topic[IO, Option[String]](None).unsafeRunSync())
  val service = JsonRPCService[IO].mountAPI[TestAPI[IO]](backend)
  val server = Server[IO](addr, service).unsafeRunSync()

  "server" should {
    "start => isUp" in {
      server.isUp.unsafeRunSync() shouldBe false
      (server.start *> server.isUp).unsafeRunSync() shouldBe true
      server.stop.unsafeRunSync()
    }

    "start stop => !isUp" in {
      (server.start *> server.stop *> server.isUp).unsafeRunSync() shouldBe false
    }
  }
}
