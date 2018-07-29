package jbok.network.client

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2.Scheduler
import fs2.async.mutable.Topic
import io.circe.syntax._
import jbok.network.JsonRPCClient
import jbok.network.json.JsonRPCMessage.RequestId
import jbok.network.json.JsonRPCResponse
import jbok.network.transport.DuplexTransport

import scala.concurrent.duration._

object Client {
  def apply[F[_]](
      transport: DuplexTransport[F, String, String],
      timeout: FiniteDuration = 10.seconds
  )(implicit F: ConcurrentEffect[F], S: Scheduler): JsonRPCClient[F] =
    new JsonRPCClient[F] {
      override def request(id: RequestId, json: String): F[String] =
        transport
          .request(id, json, timeout)
          .map(_.getOrElse(JsonRPCResponse.internalError(id, s"request $json timeout").asJson.noSpaces))

      override def getOrCreateTopic(method: String): F[Topic[F, Option[String]]] =
        transport.getOrCreateTopic(method)

      override def start: F[Unit] = transport.start

      override def stop: F[Unit] = transport.stop
    }
}
