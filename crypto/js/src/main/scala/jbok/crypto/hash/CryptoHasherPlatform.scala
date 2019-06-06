package jbok.crypto.hash

import jbok.crypto.facade.CryptoJs
import scodec.bits.ByteVector

import scala.scalajs.js

trait CryptoHasherPlatform {
  implicit val kec256Platform: CryptoHasher[Keccak256] = new CryptoHasher[Keccak256] {
    override def hash(bytes: ByteVector): ByteVector = {
      val bin = CryptoJs.sha3(CryptoJs.Hex.parse(bytes.toHex), js.Dynamic.literal(outputLength = 256))
      val hex = CryptoJs.Hex.stringify(bin)
      ByteVector.fromValidHex(hex)
    }
  }

  implicit val kec512Platform: CryptoHasher[Keccak512] = new CryptoHasher[Keccak512] {
    override def hash(bytes: ByteVector): ByteVector = {
      val bin = CryptoJs.sha3(CryptoJs.Hex.parse(bytes.toHex), js.Dynamic.literal(outputLength = 512))
      val hex = CryptoJs.Hex.stringify(bin)
      ByteVector.fromValidHex(hex)
    }
  }

  implicit val sha256Platform: CryptoHasher[SHA256] = new CryptoHasher[SHA256] {
    override def hash(bytes: ByteVector): ByteVector = {
      val bin = CryptoJs.sha256(CryptoJs.Hex.parse(bytes.toHex))
      val hex = CryptoJs.Hex.stringify(bin)
      ByteVector.fromValidHex(hex)
    }
  }

  implicit val ripemd160Platform: CryptoHasher[RipeMD160] = new CryptoHasher[RipeMD160] {
    override def hash(bytes: ByteVector): ByteVector = {
      val bin = CryptoJs.ripemd160(CryptoJs.Hex.parse(bytes.toHex))
      val hex = CryptoJs.Hex.stringify(bin)
      ByteVector.fromValidHex(hex)
    }
  }
}
