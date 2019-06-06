package jbok.network.tcp

import cats.effect.{Concurrent, ContextShift, IO, Sync}
import cats.implicits._
import fs2.Chunk
import fs2.io.tcp.Socket
import javax.net.ssl.SSLContext
import jbok.common.thread.ThreadUtil
import jbok.crypto.ssl.SSLContextHelper
import jbok.network.Message
import spinoco.fs2.crypto.io.tcp.TLSSocket

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object implicits {
  val maxBytes: Int           = 4 * 1024 * 1024
  val timeout                 = Some(10.seconds)
  val sslEC: ExecutionContext = ThreadUtil.blockingThreadPool[IO]("jbok-tls").allocated.unsafeRunSync()._1

  implicit class TcpSocketOps[F[_]](val socket: Socket[F]) extends AnyVal {
    def readMessage(implicit F: Sync[F]): F[Message[F]] =
      socket.read(maxBytes, timeout).flatMap {
        case Some(chunk) => Message.decodeChunk(chunk)
        case None        => F.raiseError(new Exception(s"socket already closed"))
      }

    def writeMessage(message: Message[F]): F[Unit] =
      socket.write(Chunk.array(Message.encodeBytes(message).byteArray), timeout)

    def toTLSSocket(sslOpt: Option[SSLContext], client: Boolean)(implicit F: Concurrent[F], cs: ContextShift[F]): F[Socket[F]] =
      sslOpt match {
        case Some(ssl) =>
          if (client) TLSSocket.instance(socket, SSLContextHelper.clientEngine(ssl).engine, sslEC).widen[Socket[F]]
          else TLSSocket.instance(socket, SSLContextHelper.serverEngine(ssl).engine, sslEC).widen[Socket[F]]
        case None => F.pure(socket)
      }
  }
}
