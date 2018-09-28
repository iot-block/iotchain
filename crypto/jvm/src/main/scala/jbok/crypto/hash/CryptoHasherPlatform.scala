package jbok.crypto.hash

trait CryptoHasherPlatform {
  implicit val kec256Platform: CryptoHasher[Keccak256] = new CryptoHasher[Keccak256] {
    override def hash(bytes: Array[Byte]): Array[Byte] = BouncyHash.kec256(bytes)
  }

  implicit val kec512Platform: CryptoHasher[Keccak512] = new CryptoHasher[Keccak512] {
    override def hash(bytes: Array[Byte]): Array[Byte] = BouncyHash.kec512(bytes)
  }

  implicit val sha256Platform: CryptoHasher[SHA256] = new CryptoHasher[SHA256] {
    override def hash(bytes: Array[Byte]): Array[Byte] = BouncyHash.sha256.digest(bytes)
  }

  implicit val ripemd160Platform: CryptoHasher[RipeMD160] = new CryptoHasher[RipeMD160] {
    override def hash(bytes: Array[Byte]): Array[Byte] = BouncyHash.ripemd160.digest(bytes)
  }
}
