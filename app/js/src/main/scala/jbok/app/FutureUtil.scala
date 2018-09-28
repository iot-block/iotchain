package jbok.app

import scala.concurrent.{Future, Promise}
import scala.scalajs.js

object FutureUtil {
  def delay(milliseconds: Int): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }
    p.future
  }
}
