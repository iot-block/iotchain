package jbok.network.facade

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("isomorphic-ws", JSImport.Default)
class WebSocket(url: String) extends js.Object {
  def on(str: String, fun: js.Function1[js.Object, _]): Unit = js.native

  var onopen: js.Function1[Event, _] = js.native

  var onerror: js.Function1[ErrorEvent, _] = js.native

  var onclose: js.Function1[CloseEvent, _] = js.native

  var onmessage: js.Function1[MessageEvent, _] = js.native

  var binaryType: js.Any = js.native

  def send(data: String): Unit = js.native

  def close(): Unit = js.native
}

object WebSocket {
  def apply(url: String): WebSocket = new WebSocket(url)
}

class Event(var target: js.Any, var `type`: js.Any) extends js.Object

class MessageEvent(var data: js.Any, var target: js.Any, var `type`: js.Any) extends js.Object

class CloseEvent(var code: Int, var reason: String, var target: js.Any) extends js.Object

class ErrorEvent(var error: js.Any, var message: js.Any) extends js.Object
