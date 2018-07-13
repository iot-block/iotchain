package jbok.crypto.facade

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.Uint8Array

@js.native
@JSImport("elliptic", "eddsa")
class EdDSA(curve: String) extends js.Object {
  def keyPair(options: js.Dynamic): KeyPairED = js.native
  def keyFromPublic(pub: String, enc: String): KeyPairED = js.native
  def keyFromSecret(priv: String, enc: String): KeyPairED = js.native
}

@js.native
@JSImport("elliptic", "ec")
class EC(curve: String) extends js.Object {
  def genKeyPair(options: Option[js.Dynamic] = None): KeyPairEC = js.native
  def keyPair(options: js.Dynamic): KeyPairEC = js.native
  def keyFromPublic(pub: String, enc: String): KeyPairEC = js.native
  def keyFromPrivate(priv: String, enc: String): KeyPairEC = js.native
}

@js.native
@JSImport("elliptic", "ec")
class KeyPairEC(ec: EC, options: js.Dynamic) extends js.Object {
  def verify(msg: Uint8Array, signature: String): Boolean = js.native
  def sign(msg: Uint8Array): SignatureEC = js.native

  def getPublic(compact: Boolean, enc: String): String = js.native
  def getPrivate(enc: String): String = js.native
  val priv: js.Any = js.native
  val pub: js.Any = js.native
}

@js.native
@JSImport("elliptic", "eddsa")
class KeyPairED(ec: EC, options: js.Dynamic) extends js.Object {
  def verify(msg: Uint8Array, signature: String): Boolean = js.native
  def sign(msg: Uint8Array): SignatureED = js.native

  def getPublic(enc: String): String = js.native
  def getSecret(enc: String): String = js.native

  def pubBytes(): Uint8Array = js.native
  def privBytes(): Uint8Array = js.native
}

@js.native
@JSImport("elliptic", "ec")
class SignatureEC(der: String, enc: String = "hex") extends js.Object {
  def toDER(enc: String): String = js.native
}

@js.native
@JSImport("elliptic", "eddsa")
class SignatureED(der: String, enc: String = "hex") extends js.Object {
  def toHex(): String = js.native
}
