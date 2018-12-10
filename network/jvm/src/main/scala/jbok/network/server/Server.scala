package jbok.network.server
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup
import java.util.concurrent.{Executors, TimeUnit}

import cats.effect._
import cats.implicits._
import com.codahale.metrics.ConsoleReporter
import fs2._
import fs2.concurrent.Queue
import jbok.network.common.{RequestId, TcpUtil}
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import org.http4s.{EntityDecoder, HttpRoutes, Request, Response, StaticFile}
import scodec.Codec
import scodec.bits.BitVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

final case class Server[F[_]](bind: InetSocketAddress, stream: Stream[F, Unit])

object Server {
  def tcp[F[_], A: Codec: RequestId](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): Server[F] = {
    val stream = fs2.io.tcp.Socket
      .server[F](bind)
      .map(s =>
        for {
          conn <- Stream.eval(TcpUtil.socketToConnection[F, A](s, true))
          _    <- Stream.eval(conn.start)
          _    <- conn.reads.through(pipe).to(conn.sink)
        } yield ())
      .parJoin(maxOpen)

    Server(bind, stream)
  }

  def websocket[F[_], A: Codec](bind: InetSocketAddress, pipe: Pipe[F, A, A], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): Server[F] = {

    val stream: Stream[F, Unit] = {
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

      val httpApp = Router("/" -> service).orNotFound

      val builder = BlazeServerBuilder[F]
        .bindSocketAddress(bind)
        .withHttpApp(httpApp)
        .withWebSockets(true)
        .withoutBanner
        .withIdleTimeout(Duration.Inf)

      builder.serve.drain
    }

    Server(bind, stream)
  }

  def http[F[_]](bind: InetSocketAddress, handle: String => F[String])(
      implicit F: ConcurrentEffect[F],
      T: Timer[F],
      cs: ContextShift[F]
  ): Server[F] = {
    val stream: Stream[F, Unit] = {
      val dsl = Http4sDsl[F]
      import dsl._

      val blockEC = ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(4))

      // TODO: fix resource path
      def static(file: String, blockingEc: ExecutionContext, request: Request[F]): F[Response[F]] =
        StaticFile
          .fromResource[F]("/META-INF/resources/webjars/jbok-app/0.0.1-SNAPSHOT/" + file, blockingEc, Some(request))
          .getOrElseF(NotFound())

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

      import com.codahale.metrics.SharedMetricRegistries
      import org.http4s.metrics.dropwizard.Dropwizard
      import org.http4s.server.middleware.Metrics
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

      BlazeServerBuilder[F]
        .bindSocketAddress(bind)
        .withHttpApp(httpApp)
        .serve
        .drain
    }
    Server(bind, stream)
  }
}
