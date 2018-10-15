package jbok.core.sync
import cats.effect.Effect

import scala.concurrent.ExecutionContext

case class LightSync[F[_]]()(implicit F: Effect[F], EC: ExecutionContext)
