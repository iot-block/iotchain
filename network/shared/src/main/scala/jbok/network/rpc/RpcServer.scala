package jbok.network.rpc

import _root_.io.circe._
import _root_.io.circe.generic.auto._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import cats.effect.{Concurrent, IO}
import fs2._
import fs2.concurrent.Queue
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.json.{JsonRPCNotification, JsonRPCResponse}
import jbok.network.rpc.RpcServer._
import scodec.Codec

import scala.language.experimental.macros

class RpcServer(
    val handlers: Map[String, String => IO[String]],
    val queue: Queue[IO, String]
)(implicit F: Concurrent[IO]) {
  private[this] val log = org.log4s.getLogger

  def mountAPI[API](api: API): RpcServer = macro RpcServerMacro.mountAPI[RpcServer, API]

  def addHandlers(handlers: List[(String, String => IO[String])]): RpcServer =
    new RpcServer(this.handlers ++ handlers.toMap, queue)

  def handle(req: String): IO[String] = {
    requestMethod.method(req) match {
      case Some(m) =>
        log.debug(s"handling request ${req}")
        handlers.get(m) match {
          case Some(f) =>
            f(req).attempt.map {
              case Left(e) =>
                log.debug(s"internal error: ${e.toString}")
                JsonRPCResponse.internalError(e.toString).asJson.noSpaces

              case Right(resp) =>
                log.debug(s"response: ${resp}")
                resp
            }
          case None =>
            log.debug(s"method not found: ${m}")
            IO.pure(JsonRPCResponse.methodNotFound(m, RequestId[String].id(req)).asJson.noSpaces)
        }
      case None =>
        IO.pure(JsonRPCResponse.invalidRequest("no method specified").asJson.noSpaces)
    }
  }

  val pipe: Pipe[IO, String, String] = { input =>
    val handles = input.evalMap(handle)
    queue.dequeue.merge(handles)
  }

  def notify[A: Encoder](method: String, a: A): IO[Unit] = {
    val notification: String = JsonRPCNotification(method, a).asJson.noSpaces
    queue.enqueue1(notification)
  }
}

object RpcServer {
  implicit val requestId: RequestId[String] = new RequestId[String] {
    override def id(a: String): Option[String] =
      parse(a).flatMap(_.hcursor.downField("id").as[String]).toOption
  }

  implicit val requestMethod: RequestMethod[String] = new RequestMethod[String] {
    override def method(a: String): Option[String] =
      parse(a).flatMap(_.hcursor.downField("method").as[String]).toOption
  }

  implicit val codec: Codec[String] = jbok.codec.rlp.codecs.rstring.codec

  def apply(
      handlers: Map[String, String => IO[String]] = Map.empty
  )(implicit F: Concurrent[IO]): IO[RpcServer] =
    for {
      queue <- Queue.bounded[IO, String](32)
    } yield new RpcServer(handlers, queue)
}
