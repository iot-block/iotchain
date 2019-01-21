package jbok.network.server

import java.net.InetSocketAddress

import _root_.io.prometheus.client.CollectorRegistry
import _root_.io.circe.parser._
import _root_.io.circe._
import cats.effect._
import cats.implicits._
import fs2._
import fs2.concurrent.Queue
import jbok.common.metrics.Metrics
import jbok.network.{Message, Request}
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.implicits._
import org.http4s.metrics.prometheus.PrometheusExportService
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebSocketFrame
import scodec.bits.ByteVector

import scala.concurrent.duration._

object WsServer {
  private[this] val log = jbok.common.log.getLogger("WsServer")

  def bind[F[_]](
      bind: InetSocketAddress,
      pipe: Pipe[F, Message[F], Message[F]],
      metrics: Metrics[F],
      handler: Option[Message[F] => F[Message[F]]] = None,
      maxOpen: Int = Int.MaxValue
  )(
      implicit F: ConcurrentEffect[F],
      T: Timer[F]
  ): Server[F] = {

    val registry: Option[CollectorRegistry] = metrics.registry match {
      case x: CollectorRegistry => Some(x)
      case _                    => None
    }

    val registerDefaults =
      registry.fold(Stream.empty.covaryAll[F, Unit])(r =>
        Stream.eval[F, Unit](PrometheusExportService.addDefaults[F](r)))

    val stream: Stream[F, Unit] = registerDefaults ++ Stream.eval_(
      F.delay(log.info(s"http server successfully bound to ${bind}"))) ++ {
      val dsl = Http4sDsl[F]
      import dsl._
      val service = HttpRoutes.of[F] {
        case GET -> Root =>
          Queue.unbounded[F, Message[F]].flatMap { queue =>
            val toClient = queue.dequeue.evalMap { x =>
              x.asBytes.map(bytes => WebSocketFrame.Binary(bytes, true))
            }

            val fromClient: Sink[F, WebSocketFrame] = { s: Stream[F, WebSocketFrame] =>
              s.evalMap {
                  case WebSocketFrame.Binary(bytes, _) =>
                    log.debug(s"received ${bytes} from client")
                    Message.decodeBytes[F](bytes)

                  case WebSocketFrame.Text(text, _) =>
                    log.debug(s"received ${text} from client")
                    Message.decodeBytes[F](ByteVector.fromValidBase64(text))
                }
                .through(pipe)
                .to(queue.enqueue)
            }

            WebSocketBuilder[F].build(toClient, fromClient)
          }

        case req @ POST -> Root =>
          handler match {
            case Some(h) =>
              for {
                text    <- req.as[String]
                request <- Request.fromJson[F](parse(text).getOrElse(Json.Null))
                result  <- h(request).flatMap(_.asJson.map(_.noSpaces))
                resp    <- Ok(result)
              } yield resp

            case None =>
              Forbidden()
          }
      }

      val httpApp = registry match {
        case Some(r) => Router("/" -> service, "/" -> PrometheusExportService(r).routes).orNotFound
        case None    => Router("/" -> service).orNotFound
      }

      val builder = BlazeServerBuilder[F]
        .bindSocketAddress(bind)
        .withHttpApp(httpApp)
        .withWebSockets(true)
        .withoutBanner
        .withIdleTimeout(60.seconds)

      builder.serve.drain
        .onFinalize(F.delay(log.info(s"stop listening to ${bind}")))
    }

    Server(bind, stream)
  }
}
