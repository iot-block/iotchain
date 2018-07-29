package jbok.network.transport

import java.nio.channels.AsynchronousChannelGroup
import java.nio.charset.StandardCharsets

import cats.effect.{ConcurrentEffect, Effect}
import cats.implicits._
import fs2.async.mutable.{Signal, Topic}
import fs2.async.{Promise, Ref}
import io.circe.Json
import io.circe.parser._
import fs2._
import jbok.network.NetAddress

import scala.concurrent.ExecutionContext

abstract class WSTransport[F[_]](addr: NetAddress)(implicit F: Effect[F], ec: ExecutionContext)
    extends DuplexTransport[F, String, String](addr)

object WSTransport {
  import spinoco.fs2.http.websocket.{Frame, WebSocket, WebSocketRequest}
  import spinoco.protocol.http.Uri.QueryParameter

  def apply[F[_]](addr: NetAddress, maxQueued: Int = 64)(
      implicit F: ConcurrentEffect[F],
      AG: AsynchronousChannelGroup,
      S: Scheduler,
      EC: ExecutionContext
  ): F[WSTransport[F]] =
    for {
      _topics <- fs2.async.refOf[F, Map[String, Topic[F, Option[String]]]](Map.empty)
      _promises <- fs2.async.refOf[F, Map[String, Promise[F, String]]](Map.empty)
      _stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
      queue <- fs2.async.boundedQueue[F, String](maxQueued)
    } yield
      new WSTransport[F](addr) {
        override val topics: Ref[F, Map[String, Topic[F, Option[String]]]] = _topics

        override val promises: Ref[F, Map[String, Promise[F, String]]] = _promises

        override val stopWhenTrue: Signal[F, Boolean] = _stopWhenTrue

        override def parseId(x: String): F[(Option[String], String)] =
          (parse(x).getOrElse(Json.Null).hcursor.get[String]("id") match {
            case Left(e)   => (None, x)
            case Right(id) => (Some(id), x)
          }).pure[F]

        override def parseMethod(x: String): F[(String, String)] =
          F.delay(parse(x).getOrElse(Json.Null).hcursor.get[String]("method").right.get -> x)

        val pipe: Pipe[F, Frame[String], Frame[String]] = { frame =>
          val inbound: Stream[F, Unit] =
            frame
              .evalMap {
                case Frame.Text(x)   => handle(x)
                case Frame.Binary(_) => ???
              }

          val outbound = queue.dequeue.map(x => Frame.Text(x))

          outbound.concurrently(inbound)
        }

        override def send(req: String): F[Unit] = queue.enqueue1(req)

        override def start: F[Unit] = {
          implicit val codec: scodec.Codec[String] = scodec.codecs.string(StandardCharsets.UTF_8)

          val request = addr.port match {
            case Some(p) => WebSocketRequest.ws(addr.host, p, "/", QueryParameter.single("encoding", "text"))
            case None    => WebSocketRequest.ws(addr.host, "/", QueryParameter.single("encoding", "text"))
          }

          val stream = WebSocket.client(request, pipe).interruptWhen(stopWhenTrue)

          stopWhenTrue.set(false) *> F.start(stream.compile.drain).void
        }

        override def stop: F[Unit] = stopWhenTrue.set(true)
      }
}
