package jbok.crypto.signature.ecdsa

import cats.effect.IO
import jbok.crypto.signature.ECDSAPlatform

object SecP256k1 extends ECDSAPlatform[IO]("secp256k1")

