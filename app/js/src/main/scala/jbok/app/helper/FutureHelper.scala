package jbok.app.helper

import scala.concurrent.{Future, Promise}
import scala.scalajs.js

object FutureHelper {
  def delay(milliseconds: Int): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }
    p.future
  }
}
