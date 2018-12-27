package jbok.network.facade

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("axios", JSImport.Namespace)
object Axios extends js.Object {
  def get(url: String): js.Promise[Response] = js.native
}

@js.native
trait Response extends js.Object {
  def data: String = js.native

  def status: Int = js.native

  def statusText: String = js.native
}
