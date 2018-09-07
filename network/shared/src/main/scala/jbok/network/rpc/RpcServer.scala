package jbok.network.rpc

import java.nio.charset.StandardCharsets

import _root_.io.circe._
import _root_.io.circe.generic.auto._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import cats.effect.IO
import fs2._
import fs2.async.mutable.Queue
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.json.{JsonRPCNotification, JsonRPCResponse}
import jbok.network.rpc.RpcServer._
import scodec.Codec
import scodec.codecs._

import scala.language.experimental.macros
import jbok.network.execution._

class RpcServer(
    val handlers: Map[String, String => IO[String]],
    val queue: Queue[IO, String]
) {
  def mountAPI[API <: RpcAPI](api: API): RpcServer = macro RpcServerMacro.mountAPI[RpcServer, API]

  val pipe: Pipe[IO, String, String] = { input =>
    val s = input.evalMap { s =>
      RequestMethod[String].method(s) match {
        case Some(m) =>
          handlers.get(m) match {
            case Some(f) =>
              f(s).attempt.map {
                case Left(e)     => JsonRPCResponse.internalError(e.toString).asJson.noSpaces
                case Right(resp) => resp
              }
            case None =>
              IO.pure(JsonRPCResponse.methodNotFound(m, RequestId[String].id(s)).asJson.noSpaces)
          }
        case None =>
          IO.pure(JsonRPCResponse.invalidRequest("no method specified").asJson.noSpaces)
      }
    }

    Stream(queue.dequeue, s).joinUnbounded
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

  implicit val codec: Codec[String] = variableSizeBytes(uint16, string(StandardCharsets.UTF_8))

  def apply(
      handlers: Map[String, String => IO[String]] = Map.empty
  ): IO[RpcServer] =
    for {
      queue <- fs2.async.boundedQueue[IO, String](32)
    } yield new RpcServer(handlers, queue)
}
