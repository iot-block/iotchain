package jbok.crypto.signature

import cats.effect.IO
import jbok.crypto.signature.ecdsa.SecP256k1

trait SignaturePlatform {
  val ecdsaPlatform: Signature[IO, ECDSA] = SecP256k1
}
