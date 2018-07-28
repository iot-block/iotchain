package jbok.core.keystore

import cats.effect.IO
import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.crypto.signature.{KeyPair, SecP256k1}

case class Wallet(address: Address, secret: KeyPair.Secret) {
  val keyPair: KeyPair =
    SecP256k1.buildPublicKeyFromPrivate[IO](secret).map(public => KeyPair(public, secret)).unsafeRunSync()

  def signTx(tx: Transaction, chainId: Option[Byte]): SignedTransaction =
    SignedTransaction.sign(tx, keyPair, chainId)
}
