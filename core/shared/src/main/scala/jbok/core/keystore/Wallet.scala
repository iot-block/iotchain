package jbok.core.keystore

import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}

case class Wallet(address: Address, secret: KeyPair.Secret)(implicit chainId: BigInt) {
  val keyPair: KeyPair =
    Signature[ECDSA].generatePublicKey(secret).map(public => KeyPair(public, secret)).unsafeRunSync()

  def signTx(tx: Transaction): SignedTransaction =
    SignedTransaction.sign(tx, keyPair, chainId)
}
