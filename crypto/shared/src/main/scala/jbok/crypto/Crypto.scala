package jbok.crypto

object Crypto {
  type KeyPairGenerator[F[_]] = Option[Array[Byte]] => F[KeyPair]
}
