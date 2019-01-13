package jbok.network.facade

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("axios", JSImport.Namespace)
object Axios extends js.Object {
  def get(url: String): js.Promise[Response] = js.native

  def post(url: String, data: String): js.Promise[Response] = js.native

  def request(config: Config): js.Promise[Response] = js.native
}

@js.native
trait Response extends js.Object {
  def data: String = js.native

  def status: Int = js.native

  def statusText: String = js.native
}

class Config(val url: String) extends js.Object {
  val method: String = "get"

  val data: js.Any = js.undefined

  val timeout: Int = 0 // milliseconds, default is `0` (no timeout)

  val withCredentials: Boolean = false // whether or not cross-site Access-Control requests

  val responseType: String = "text" // arraybuffer, blob, document, json, text, stream

  val responseEncoding: String = "utf8"

  val xsrfCookieName: String = "XSRF-TOKEN" // name of the cookie to use as a value for xsrf token

  val xsrfHeaderName: String = "X-XSRF-TOKEN" // name of the http header that carries the xsrf token value

  val maxRedirects: Int = 5 // If set to 0, no redirects will be followed

  val transformResponse: js.Any = js.undefined
}
