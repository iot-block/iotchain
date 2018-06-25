package jbok.rpc.server

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2.StreamApp.ExitCode
import fs2._
import fs2.async.mutable.{Queue, Signal}
import jbok.rpc.Address
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}
import org.http4s.{EntityDecoder, EntityEncoder, HttpService}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

abstract class Server[F[_]](val addr: Address, handle: String => F[String])(
    implicit F: ConcurrentEffect[F],
    ec: ExecutionContext)
    extends Http4sDsl[F]
    with Http4sClientDsl[F] {
  private[this] val logger = org.log4s.getLogger

  implicit val encoder: EntityEncoder[F, String] = jsonEncoderOf[F, String]

  implicit val decoder: EntityDecoder[F, String] = jsonOf[F, String]

  val queue: Queue[F, String]

  def push1(msg: String): F[Unit] = {
    queue.enqueue1(msg)
  }

  def push: Sink[F, String] = _.evalMap(push1)

  def wsService = HttpService[F] {
    case GET -> Root =>
      val toClient: Pipe[F, String, WebSocketFrame] = _.map(m => Text(m))

      val fromClient: Pipe[F, WebSocketFrame, String] = _.evalMap[String] {
        case Text(t, _) =>
          logger.info(s"fromClient ${t}")
          handle(t)
      }

      val to: Stream[F, WebSocketFrame] = queue.dequeue.through(toClient)
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

  val stopWhenTrue: Signal[F, Boolean]

  def start: F[Unit] = {
    for {
      exitCode <- fs2.async.refOf[F, ExitCode](ExitCode.Success)
      _ <- stopWhenTrue.set(false)
      _ <- F.start(build.serveWhile(stopWhenTrue, exitCode).compile.drain)
    } yield ()
  }

  def serve: Stream[F, StreamApp.ExitCode] =
    Stream.eval(stopWhenTrue.set(false)).flatMap(_ => build.serve.interruptWhen(stopWhenTrue))

  def stop: F[Unit] = stopWhenTrue.set(true)

  def isUp: F[Boolean] = stopWhenTrue.get.map(!_)
}

object Server {
  def apply[F[_]: ConcurrentEffect](
      addr: Address,
      handle: String => F[String]
  )(implicit ec: ExecutionContext): F[Server[F]] = {
    for {
      _queue <- fs2.async.unboundedQueue[F, String]
      _stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
    } yield
      new Server[F](addr, handle) {
        override val queue = _queue
        override val stopWhenTrue: Signal[F, Boolean] = _stopWhenTrue
      }
  }
}
