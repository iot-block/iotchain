package jbok.network

import fs2.Scheduler

import scala.concurrent.ExecutionContext

object JsExecution {
  implicit val EC: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  implicit val Sch: Scheduler = Scheduler.default
}
