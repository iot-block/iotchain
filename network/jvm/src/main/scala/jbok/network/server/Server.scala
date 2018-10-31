package jbok.network.server
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{Executors, TimeUnit}

import cats.effect.implicits._
import cats.effect._
import cats.implicits._
import com.codahale.metrics.ConsoleReporter
import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import jbok.network.common.TcpUtil
import org.http4s.{EntityDecoder, HttpRoutes, Request, Response, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.blaze.{BlazeBuilder, BlazeServerBuilder}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.ExecutionContext

trait Server[F[_]] {
  def bindAddress: InetSocketAddress

  def haltWhenTrue: SignallingRef[F, Boolean]

  def serve: Stream[F, Unit]

  def start(implicit F: Concurrent[F]): F[Unit] =
    haltWhenTrue.get.flatMap {
      case false => F.unit
      case true  => haltWhenTrue.set(false) *> serve.interruptWhen(haltWhenTrue).compile.drain.start.void
    }

  def stop(implicit F: Concurrent[F]): F[Unit] =
    haltWhenTrue.set(true)
}

object Server {
  def tcp[F[_], A: Codec](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): F[Server[F]] =
    for {
      signal <- SignallingRef[F, Boolean](true)
    } yield
      new Server[F] {
        private[this] val log = org.log4s.getLogger

        override val bindAddress: InetSocketAddress = bind

        override val haltWhenTrue: SignallingRef[F, Boolean] = signal

        override val serve: Stream[F, Unit] =
          fs2.io.tcp
            .serverWithLocalAddress[F](bind)
            .map {
              case Left(bindAddr) =>
                log.info(s"server bound to ${bindAddr}")
                Stream.empty.covary[F]

              case Right(s) =>
                Stream
                  .resource(s)
                  .flatMap(socket => {
                    for {
                      conn <- Stream.eval(TcpUtil.socketToConnection[F](socket, true))
                      _ <- conn
                        .reads[A]()
                        .through(pipe)
                        .to(conn.writes[A]())
                    } yield ()
                  })
            }
            .parJoin(maxOpen)
            .handleErrorWith(e => Stream.eval[F, Unit](F.delay(log.error(e)(s"server error: ${e}"))))
      }

  def websocket[F[_], A: Codec](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): F[Server[F]] =
    for {
      signal <- SignallingRef[F, Boolean](true)
    } yield
      new Server[F] {
        private[this] val log = org.log4s.getLogger

        override val bindAddress: InetSocketAddress = bind

        override val haltWhenTrue: SignallingRef[F, Boolean] = signal

        override val serve: Stream[F, Unit] = {
          val dsl = Http4sDsl[F]
          import dsl._
          val service = HttpRoutes.of[F] {
            case GET -> Root =>
              Queue.unbounded[F, A].flatMap { queue =>
                val toClient = queue.dequeue.map { x =>
                  val bytes = Codec[A].encode(x).require.bytes
                  WebSocketFrame.Binary(bytes, true)
                }

                val fromClient: Sink[F, WebSocketFrame] = { s: Stream[F, WebSocketFrame] =>
                  s.map {
                      case WebSocketFrame.Binary(bytes, _) =>
                        Codec[A].decode(bytes.bits).require.value

                      case WebSocketFrame.Text(text, _) =>
                        Codec[A].decode(BitVector.fromValidBase64(text)).require.value
                    }
                    .through(pipe)
                    .to(queue.enqueue)
                }

                WebSocketBuilder[F].build(toClient, fromClient)
              }
          }

          val builder = BlazeBuilder[F]
            .bindSocketAddress(bind)
            .mountService(service, "/")
            .withWebSockets(true)
            .withoutBanner

          log.info(s"start websocket server at ${bind}")
          builder.serve.drain
        }
      }

  def http[F[_]](bind: InetSocketAddress, handle: String => F[String])(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      cs: ContextShift[F]
  ): F[Server[F]] =
    for {
      signal <- SignallingRef[F, Boolean](true)
    } yield
      new Server[F] {
        private[this] val log = org.log4s.getLogger

        override val bindAddress: InetSocketAddress = bind

        override val haltWhenTrue: SignallingRef[F, Boolean] = signal

        override val serve: Stream[F, Unit] = {
          val dsl = Http4sDsl[F]
          import dsl._

          val blockEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

          // TODO: fix resource path
          def static(file: String, blockingEc: ExecutionContext, request: Request[F]): F[Response[F]] = {
            StaticFile
              .fromResource[F]("/META-INF/resources/webjars/jbok-app/0.0.1-SNAPSHOT/" + file, blockingEc, Some(request))
              .getOrElseF(NotFound())
          }

          val service = HttpRoutes.of[F] {
            case request @ GET -> Root / path if List(".js", ".css", ".map", ".html", ".webm").exists(path.endsWith) =>
              static(path, blockEC, request)

            case request @ POST -> Root / "api" =>
              EntityDecoder.text
                .decode(request, true)
                .semiflatMap { req =>
                  handle(req).flatMap(res => Ok.apply(res))
                }
                .getOrElseF(BadRequest())
          }

          import org.http4s.server.middleware.Metrics
          import org.http4s.metrics.dropwizard.Dropwizard
          import com.codahale.metrics.SharedMetricRegistries
          implicit val clock = Clock.create[F]
          val registry       = SharedMetricRegistries.getOrCreate("default")
          val meteredRoutes  = Metrics[F](Dropwizard(registry, "server"))(service)
          val reporter = ConsoleReporter
            .forRegistry(registry)
            .convertRatesTo(TimeUnit.SECONDS)
            .convertDurationsTo(TimeUnit.MILLISECONDS)
            .build()
          reporter.start(1, TimeUnit.MINUTES)

          val httpApp = Router("/" -> meteredRoutes).orNotFound

          log.info(s"start serving http at ${bind}")

          BlazeServerBuilder[F]
            .bindSocketAddress(bind)
            .withHttpApp(httpApp)
            .serve
            .drain
        }
      }
}
