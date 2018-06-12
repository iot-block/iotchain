package jbok.rpc

import java.net.InetSocketAddress

import cats.data.Kleisli
import cats.effect.Effect
import cats.implicits._
import fs2.StreamApp.ExitCode
import io.circe.parser._
import io.circe.syntax._
import fs2._
import fs2.async.Ref
import fs2.async.mutable.Queue
import jbok.rpc.RpcServer.JsonrpcService
import jbok.rpc.json._
import org.http4s.circe._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Server
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.server.websocket.WebSocketBuilder
import org.http4s.websocket.WebsocketBits.{Text, WebSocketFrame}
import org.http4s.{EntityDecoder, EntityEncoder, HttpService, Uri}

import scala.concurrent.ExecutionContext

abstract class RpcServer[F[_]](
    val addr: InetSocketAddress,
    val service: JsonrpcService[F]
)(implicit F: Effect[F])
    extends Http4sDsl[F]
    with Http4sClientDsl[F] {

  implicit val encoder: EntityEncoder[F, JsonrpcMsg] = jsonEncoderOf[F, JsonrpcMsg]
  implicit val decoder: EntityDecoder[F, JsonrpcMsg] = jsonOf[F, JsonrpcMsg]

  lazy val uri: Uri = Uri.unsafeFromString(s"http://${addr.getHostString}:${addr.getPort}")

  val server: Ref[F, Option[Server[F]]]

  def isUp: F[Boolean] = server.get.map(_.isDefined)

  val corsConfig = CORSConfig(
    anyOrigin = true,
    anyMethod = true,
    allowCredentials = true,
    maxAge = Long.MaxValue
  )

  val queue: Queue[F, JsonrpcMsg]

  def push1(msg: JsonrpcNotification): F[Unit] = {
    queue.enqueue1(msg)
  }

  def push: Sink[F, JsonrpcNotification] = _.evalMap(push1)

  val httpService: HttpService[F] = HttpService[F] {
    case req @ POST -> Root =>
      for {
        reqMsg <- req.as[JsonrpcMsg]
        respMsg <- service(reqMsg)
        resp <- Ok(respMsg.asJson)
      } yield resp
  }

  def wsService(implicit ec: ExecutionContext) = HttpService[F] {
    case GET -> Root =>
      val toClient: Pipe[F, JsonrpcMsg, WebSocketFrame] = _.map(m => Text(m.asJson.noSpaces))

      val fromClient: Pipe[F, WebSocketFrame, JsonrpcMsg] = _.evalMap[JsonrpcMsg] {
        case Text(t, _) =>
          for {
            req <- F.delay(decode[JsonrpcMsg](t).right.get)
            resp <- service.run(req)
          } yield resp
      }

      val to: Stream[F, WebSocketFrame] = queue.dequeue.through(toClient)
      val from: Sink[F, WebSocketFrame] = fromClient.andThen(_.to(queue.enqueue))
      WebSocketBuilder[F].build(to, from)
  }

  private def build(implicit ec: ExecutionContext) =
    BlazeBuilder[F]
      .bindSocketAddress(addr)
      .mountService(CORS(httpService, corsConfig), "/")
      .mountService(wsService, "/")
      .withWebSockets(true)
      .withoutBanner

  def start(implicit ec: ExecutionContext): F[Server[F]] = {
    for {
      oldS <- server.get
      newS <- oldS match {
        case Some(v) => v.pure[F]
        case _ => build.start
      }
      _ <- server.modify(_ => Some(newS))
    } yield newS
  }

  def serve(implicit ec: ExecutionContext): Stream[F, ExitCode] = {
    for {
      signal <- Stream.eval(async.signalOf[F, Boolean](false))
      exitCode <- Stream.eval(async.refOf[F, ExitCode](ExitCode.Success))
      ec <- Stream.bracket[F, Server[F], ExitCode](start)(
        _ => signal.discrete.takeWhile(_ === false).drain ++ Stream.eval(exitCode.get),
        _ => stop
      )
    } yield ec
  }

  def stop: F[Unit] =
    for {
      s <- server.get
      _ <- s match {
        case Some(v) => v.shutdown *> server.modify(_ => None)
        case _ => ().pure[F]
      }
    } yield ()
}

object RpcServer {
  type JsonrpcService[F[_]] = Kleisli[F, JsonrpcMsg, JsonrpcMsg]
  object JsonrpcService {
    def apply[F[_]](pf: PartialFunction[JsonrpcMsg, F[JsonrpcMsg]]): JsonrpcService[F] = {
      Kleisli(pf)
    }
  }

  def defaultHandler[F[_]](implicit F: Effect[F]) = JsonrpcService[F] {
    case JsonrpcRequest(method, id, params) => F.pure(JsonrpcResponse.methodNotFound(s"method ${method} not found", id))
    case JsonrpcNotification(method, params) => F.pure(JsonrpcResponse.invalidRequest("notification"))
    case JsonrpcResponse.Success(result, id) => F.pure(JsonrpcResponse.invalidRequest("success response"))
    case JsonrpcResponse.Error(error, id) => F.pure(JsonrpcResponse.invalidRequest("error response"))
  }

  def apply[F[_]: Effect](addr: InetSocketAddress)(implicit ec: ExecutionContext): F[RpcServer[F]] = {
    for {
      _q <- fs2.async.unboundedQueue[F, JsonrpcMsg]
      ref <- fs2.async.refOf[F, Option[Server[F]]](None)
    } yield
      new RpcServer[F](addr, defaultHandler[F]) {
        override val queue = _q
        override val server = ref
      }
  }
}
