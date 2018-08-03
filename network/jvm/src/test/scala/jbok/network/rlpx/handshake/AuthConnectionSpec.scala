package jbok.network.rlpx.handshake

import java.security.SecureRandom

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.crypto.signature.SecP256k1
import jbok.network.execution._
import jbok.network.tcp
import scodec.bits._

import scala.concurrent.duration._

class AuthConnectionSpec extends JbokSpec {
  val keyPair = SecP256k1.generateKeyPair[IO].unsafeRunSync()
  val nodeId = keyPair.public.uncompressed.toHex
  val secureRandom = new SecureRandom()
  val handshaker = AuthHandshaker[IO](keyPair, secureRandom)

  "AuthConnection" should {
    "build auth connection" in {
      val server =
        for {
          server <- tcp.server[IO, AuthConnection[IO]](10000)(socket => AuthConnection.accept(socket, handshaker))
          conn <- server
          bytes <- Stream.eval(conn.read())
        } yield bytes

      val client =
        for {
          conn <- tcp.client[IO, AuthConnection[IO]](10000)(socket => AuthConnection.connect(socket, handshaker, nodeId))
          _ <- Stream.eval(conn.write(hex"deadbeef"))
        } yield ()

      val bytes = server.concurrently(Sch.sleep[IO](1.second) ++ client)
      bytes.take(1).compile.toList.unsafeRunSync() shouldBe List(Some(hex"deadbeef"))
    }
  }
}
