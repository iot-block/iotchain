package jbok.core.rlpx.handshake

import java.net.InetSocketAddress

import cats.effect.IO
import fs2._
import fs2.io.tcp.Socket
import jbok.core.CoreSpec
import jbok.core.peer.handshake.AuthHandshaker
import jbok.crypto.signature.{ECDSA, Signature}

import scala.concurrent.duration._

class AuthHandshakerSpec extends CoreSpec {
  val serverKey = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
  val clientKey = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
  val addr      = new InetSocketAddress("localhost", 9003)

  "AuthHandshakerSpec" should {
    "build auth connection" in {
      val server = Socket
        .server[IO](addr)
        .flatMap { res =>
          for {
            socket     <- Stream.resource(res)
            handshaker <- Stream.eval(AuthHandshaker[IO](serverKey))
            result     <- Stream.eval(handshaker.accept(socket))
          } yield result
        }

      val client =
        for {
          socket     <- Stream.resource(Socket.client[IO](addr))
          handshaker <- Stream.eval(AuthHandshaker[IO](clientKey))
          result     <- Stream.eval(handshaker.connect(socket, serverKey.public))
        } yield result

      val result =
        server
          .merge(Stream.sleep_(1.second) ++ client)
          .take(2)
          .compile
          .toList
          .unsafeRunSync()
    }

    "fail if wrong remotePk" in {
      val server = Socket
        .server[IO](addr)
        .flatMap { res =>
          for {
            socket     <- Stream.resource(res)
            handshaker <- Stream.eval(AuthHandshaker[IO](serverKey))
            result     <- Stream.eval(handshaker.accept(socket))
          } yield result
        }

      val client =
        for {
          socket     <- Stream.resource(Socket.client[IO](addr))
          handshaker <- Stream.eval(AuthHandshaker[IO](clientKey))
          result     <- Stream.eval(handshaker.connect(socket, clientKey.public))
        } yield result

      server
        .merge(Stream.sleep_(1.second) ++ client)
        .take(2)
        .compile
        .toList
        .attempt
        .unsafeRunSync()
        .isLeft shouldBe true
    }
  }
}
