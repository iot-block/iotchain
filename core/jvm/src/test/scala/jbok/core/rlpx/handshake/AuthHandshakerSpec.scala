package jbok.core.rlpx.handshake

import java.net.InetSocketAddress

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.codec.rlp.implicits._
import jbok.common.execution._
import jbok.core.messages.Message
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.network.common.{RequestId, TcpUtil}
import scodec.Codec
import scodec.bits._

import scala.concurrent.duration._

class AuthHandshakerSpec extends JbokSpec {
  val serverKey = Signature[ECDSA].generateKeyPair().unsafeRunSync()
  val clientKey = Signature[ECDSA].generateKeyPair().unsafeRunSync()
  val addr      = new InetSocketAddress("localhost", 9003)

  implicit val I: RequestId[ByteVector] = RequestId.empty
  implicit val C: Codec[ByteVector]     = rbytes.codec

  "AuthHandshakerSpec" should {
    "build auth connection" in {
      val server = fs2.io.tcp.Socket
        .server[IO](addr)
        .evalMap[IO, AuthHandshakeResult] { res =>
          for {
            conn       <- TcpUtil.socketToConnection[IO, Message](res, true)
            fiber      <- conn.start
            handshaker <- AuthHandshaker[IO](serverKey)
            result     <- handshaker.accept(conn)
            _          <- fiber.cancel
          } yield result
        }

      val client =
        for {
          conn       <- TcpUtil.socketToConnection[IO, Message](fs2.io.tcp.Socket.client[IO](addr), false)
          _          <- conn.start
          handshaker <- AuthHandshaker[IO](clientKey)
          result     <- handshaker.connect(conn, serverKey.public)
        } yield result

      val result =
        server
          .merge(Stream.sleep_(1.second) ++ Stream.eval(client))
          .take(2)
          .compile
          .toList
          .unsafeRunSync()

      println(result)
    }

    "fail if wrong remotePk" in {
      val server = fs2.io.tcp
        .server[IO](addr)
        .evalMap[IO, Unit] { res =>
          for {
            conn       <- TcpUtil.socketToConnection[IO, Message](res, true)
            fiber      <- conn.start
            handshaker <- AuthHandshaker[IO](serverKey)
            result     <- handshaker.accept(conn)
            _ = println(result)
            _ <- fiber.cancel
          } yield ()
        }

      val client =
        for {
          conn       <- TcpUtil.socketToConnection[IO, Message](fs2.io.tcp.client[IO](addr), false)
          _          <- conn.start
          handshaker <- AuthHandshaker[IO](clientKey)
          result     <- handshaker.connect(conn, clientKey.public)
          _ = println(result)
        } yield ()

      server
        .concurrently(Stream.sleep(1.second) ++ Stream.eval(client))
        .compile
        .drain
        .attempt
        .unsafeRunSync()
        .isLeft shouldBe true
    }
  }
}
