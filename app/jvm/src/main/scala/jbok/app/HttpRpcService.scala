package jbok.app

import cats.effect.{ConcurrentEffect, Sync, Timer}
import io.circe.Json
import jbok.core.config.Configs.RpcConfig
import jbok.network.rpc.RpcService
import jbok.network.rpc.http.Http4sRpcServer
import jbok.sdk.api.{AdminAPI, PersonalAPI, PublicAPI}
import fs2._
import jbok.codec.impl.circe._
import _root_.io.circe.generic.auto._
import jbok.codec.json.implicits._

final class HttpRpcService[F[_]](config: RpcConfig, publicAPI: PublicAPI[F], personalAPI: PersonalAPI[F], adminAPI: AdminAPI[F])(implicit F: ConcurrentEffect[F], T: Timer[F]) {

  val rpcService = RpcService[F, Json]
    .mount[PublicAPI[F]](publicAPI)
    .mount[PersonalAPI[F]](personalAPI)
    .mount[AdminAPI[F]](adminAPI)

  val httpService = Http4sRpcServer.server[F](rpcService)

  val stream: Stream[F, Unit] =
    if (config.enabled) {
      Stream.resource(httpService).drain
    } else {
      Stream.empty
    }
}
