package jbok.network.tcp

import cats.effect.{Concurrent, ContextShift, IO, Sync}
import cats.implicits._
import fs2.Chunk.ByteVectorChunk
import fs2.io.tcp.Socket
import javax.net.ssl.{SSLContext, SSLEngine}
import jbok.common.thread.ThreadUtil
import jbok.network.Message

import scala.concurrent.duration._
import spinoco.fs2.crypto._
import spinoco.fs2.crypto.io.tcp.TLSSocket

import scala.concurrent.ExecutionContext

object implicits {
  val maxBytes: Int = 4 * 1024 * 1024
  val timeout  = Some(10.seconds)

  lazy val sslEC: ExecutionContext = ThreadUtil.blockingThreadPool[IO]("jbok-tls").allocated.unsafeRunSync()._1
  val ctx: SSLContext = SSLContext.getInstance("TLS")
  ctx.init(null, null, null)

  val engine: SSLEngine = ctx.createSSLEngine()
  engine.setUseClientMode(true)

  implicit class TcpSocketOps[F[_]](val socket: Socket[F]) extends AnyVal {
    def readMessage(implicit F: Sync[F]): F[Message[F]] =
      socket.read(maxBytes, timeout).flatMap {
        case Some(chunk) => Message.decodeChunk(chunk)
        case None        => F.raiseError(new Exception(s"socket already closed"))
      }

    def writeMessage(message: Message[F])(implicit F: Sync[F]): F[Unit] =
      Message.encodeBytes(message).flatMap { bytes =>
        socket.write(ByteVectorChunk(bytes), timeout)
      }

    def toTLSSocket(implicit F: Concurrent[F], cs: ContextShift[F]): F[TLSSocket[F]] =
      TLSSocket.instance(socket, engine, sslEC)
  }
}
