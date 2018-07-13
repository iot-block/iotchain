package jbok.crypto

import scorex.crypto.signatures.Curve25519

package object signature {
  implicit val defaultSignature = Curve25519
}
