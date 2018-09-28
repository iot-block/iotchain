package jbok.crypto.facade

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object CryptoJs {
  @js.native
  @JSImport("crypto-js/ripemd160", JSImport.Default)
  object ripemd160 extends js.Object {
    def apply(arr: Any): Any = js.native
  }

  @js.native
  @JSImport("crypto-js/sha3", JSImport.Default)
  object sha3 extends js.Object {
    def apply(arr: Any, option: js.Dynamic): Any = js.native
  }

  @js.native
  @JSImport("crypto-js/sha256", JSImport.Default)
  object sha256 extends js.Object {
    def apply(arr: Any): Any = js.native
  }

  @js.native
  @JSImport("crypto-js/enc-base64", JSImport.Default)
  object Base64 extends js.Object {
    def stringify(x: Any): String = js.native
  }

  @js.native
  @JSImport("crypto-js/enc-hex", JSImport.Default)
  object Hex extends js.Object {
    def stringify(x: Any): String = js.native
    def parse(s: String): Any = js.native
  }
}
