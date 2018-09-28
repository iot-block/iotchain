package jbok.crypto.facade

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSBracketAccess, JSGlobal, JSImport}
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSImport("elliptic", "ec")
class EC(curve: String) extends js.Object {
  def genKeyPair(options: Option[js.Dynamic] = None): KeyPairEC = js.native
  def keyPair(options: js.Dynamic): KeyPairEC                   = js.native
  def keyFromPublic(pub: String, enc: String): KeyPairEC        = js.native
  def keyFromPrivate(priv: String, enc: String): KeyPairEC      = js.native

  def sign(msg: Uint8Array, key: KeyPairEC): SignatureEC                = js.native
  def verify(msg: Uint8Array, sig: js.Dynamic, key: KeyPairEC): Boolean = js.native

  def getKeyRecoveryParam(msg: Uint8Array, signature: js.Dynamic): Int = js.native
  def recoverPubKey(msg: Uint8Array, signature: js.Dynamic, recId: Int): ECPoint              = js.native
}

@js.native
@JSImport("elliptic/ec/key", JSImport.Default)
class KeyPairEC(ec: EC, options: js.Dynamic) extends js.Object {
  def getPublic(compact: Boolean, enc: String): String = js.native
  def getPrivate(enc: String): String                  = js.native
  val priv: js.Any                                     = js.native
  val pub: js.Any                                      = js.native
}

@js.native
@JSImport("elliptic/ec/signature", JSImport.Default)
class SignatureEC() extends js.Object {
  def r: Any             = js.native
  def s: Any             = js.native
  def recoveryParam: Int = js.native
}

object SignatureEC {
  def apply(r: BN, s: BN, recoveryParam: Int): js.Dynamic =
    js.Dynamic.literal(r = r, s = s, recoveryParam = recoveryParam)
}

@js.native
@JSGlobal
class ECPoint() extends js.Object {
  def encode(enc: String, compact: Boolean): String = js.native
}
