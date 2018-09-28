package jbok.crypto.facade

import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js

@js.native
@JSImport("bn.js", JSImport.Default)
class BN(number: String, base: Int) extends js.Object
