package jbok.core.keystore

import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1

case class Wallet(address: Address, secret: KeyPair.Secret) {
  val keyPair: KeyPair =
    SecP256k1.generatePublicKey(secret).map(public => KeyPair(public, secret)).unsafeRunSync()

  def signTx(tx: Transaction, chainId: Option[Byte]): SignedTransaction =
    SignedTransaction.sign(tx, keyPair, chainId)
}
