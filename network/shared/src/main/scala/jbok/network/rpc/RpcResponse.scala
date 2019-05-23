package jbok.network.rpc

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.auto._
import io.circe.syntax._
import cats.syntax.functor._

sealed trait RpcResponse[P]
object RpcResponse {
  final case class Success[P](result: P)                                         extends RpcResponse[P]
  final case class Failure[P](code: Int, reason: String, data: Option[P] = None) extends RpcResponse[P]

  implicit val encoder: Encoder[RpcResponse[Json]] =
    Encoder.instance {
      case a @ Success(_) => a.asJson
      case b @ Failure(_, _, _) => b.asJson
    }

  implicit val decoder: Decoder[RpcResponse[Json]] =
    List[Decoder[RpcResponse[Json]]](
      Decoder[Success[Json]].widen,
      Decoder[Failure[Json]].widen,
    ).reduceLeft(_ or _)
}
