package jbok.network.server

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.Queue
import jbok.network.{JsonRPCService, NetAddress}
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}
import org.http4s.{EntityDecoder, EntityEncoder, HttpService}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

case class Server[F[_]](
    addr: NetAddress,
    serverRef: Ref[F, Option[org.http4s.server.Server[F]]],
    queue: Queue[F, String],
    handle: Ref[F, String => F[String]],
    push: Stream[F, String]
)(implicit F: ConcurrentEffect[F], ec: ExecutionContext)
    extends Http4sDsl[F]
    with Http4sClientDsl[F] {
  private[this] val log = org.log4s.getLogger

  implicit val encoder: EntityEncoder[F, String] = jsonEncoderOf[F, String]

  implicit val decoder: EntityDecoder[F, String] = jsonOf[F, String]

  def wsService = HttpService[F] {
    case GET -> Root =>
      val toClient: Pipe[F, String, WebSocketFrame] = _.map(m => {
        log.debug(s"toClient\n${m}")
        Text(m)
      })

      val fromClient: Pipe[F, WebSocketFrame, String] = _.evalMap[String] {
        case Text(t, _) =>
          log.debug(s"fromClient\n${t}")
          handle.get.flatMap(f => f(t))
      }

      val to: Stream[F, WebSocketFrame] = queue.dequeue.through(toClient).concurrently(push.to(queue.enqueue))
      val from: Sink[F, WebSocketFrame] = fromClient.andThen(_.to(queue.enqueue))
      WebSocketBuilder[F].build(to, from)
  }

  def service: HttpService[F] = {
    import org.http4s.server.middleware.RequestLogger
    RequestLogger(logHeaders = true, logBody = true)(wsService)
  }

  private def build =
    BlazeBuilder[F]
      .bindHttp(addr.port.get, addr.host)
      .mountService(service, "/")
      .withWebSockets(true)
      .withoutBanner
      .withNio2(true)
      .withIdleTimeout(Duration.Inf)

  def start: F[Unit] =
    for {
      ref <- serverRef.get
      _ <- ref match {
        case Some(_) => F.unit
        case None =>
          build.start.flatMap(s => serverRef.setSync(Some(s))) *>
            F.delay(log.info("server start"))
      }
    } yield ()

  def stop: F[Unit] = serverRef.get.flatMap {
    case Some(s) =>
      s.shutdown *> serverRef.setSync(None) *> F.delay(log.info("server stop"))
    case None => F.unit
  }

  def serve: Stream[F, StreamApp.ExitCode] = for {
    stopWhenTrue <- Stream.eval(fs2.async.signalOf[F, Boolean](false))
    _ = log.info(s"start serving on ${addr}")
    ec <- Stream.eval(stopWhenTrue.set(false)).flatMap(_ => build.serve.interruptWhen(stopWhenTrue))
  } yield ec

  def isUp: F[Boolean] =
    serverRef.get.map(_.isDefined)

  def mountService(service: JsonRPCService[F]): F[Unit] =
    handle.setSync(service.handle)
}

object Server {
  def apply[F[_]](addr: NetAddress, service: JsonRPCService[F], maxQueued: Int = 64)(
      implicit F: ConcurrentEffect[F],
      EC: ExecutionContext
  ): F[Server[F]] =
    for {
      queue <- fs2.async.boundedQueue[F, String](maxQueued)
      ref <- fs2.async.refOf[F, Option[org.http4s.server.Server[F]]](None)
      handle <- fs2.async.refOf[F, String => F[String]](service.handle)
    } yield Server[F](addr, ref, queue, handle, service.pushEvents(maxQueued))
}
