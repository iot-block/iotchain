package jbok.crypto.hash

trait CryptoHasherInstances extends CryptoHasherPlatform {
  implicit val kec256: CryptoHasher[Keccak256]   = kec256Platform
  implicit val kec512: CryptoHasher[Keccak512]   = kec512Platform
  implicit val sha256: CryptoHasher[SHA256]      = sha256Platform
  implicit val ripemd160: CryptoHasher[RipeMD160] = ripemd160Platform
}
object CryptoHasherInstances extends CryptoHasherInstances
