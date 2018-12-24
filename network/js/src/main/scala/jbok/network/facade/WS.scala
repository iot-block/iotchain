package jbok.network.facade

import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js

@js.native
@JSImport("ws", JSImport.Default)
class WebSocket(var url: String = js.native, var protocol: String = js.native) {
  var onopen: js.Function1[Event, _] = js.native

  def extensions: String = js.native

  var onmessage: js.Function1[MessageEvent, _] = js.native

  var onclose: js.Function1[Event, _] = js.native

  var onerror: js.Function1[Event, _] = js.native

  var binaryType: String = js.native

  def close(code: Int = js.native, reason: String = js.native): Unit = js.native

  def send(data: String): Unit = js.native
}

@js.native
@JSImport("ws/lib/event-target.js", JSImport.Default)
class Event(var target: js.Any, var `type`: js.Any) extends js.Object

@js.native
@JSImport("ws/lib/event-target.js", JSImport.Default)
class MessageEvent(var data: js.Any, `type`: js.Any) extends Event("message", `type`)

//@js.native
//@JSImport
//class CloseEvent()
