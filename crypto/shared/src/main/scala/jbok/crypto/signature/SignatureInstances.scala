package jbok.crypto.signature

import cats.effect.IO

trait SignatureInstances extends SignaturePlatform {
  implicit val ecdsa: Signature[IO, ECDSA] = ecdsaPlatform
}
