package jbok.core.keystore

import cats.effect.Sync
import cats.implicits._
import jbok.core.models.{Address, ChainId, SignedTransaction, Transaction}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}

final case class Wallet(address: Address, keyPair: KeyPair) {
  def signTx[F[_]: Sync](tx: Transaction, chainId: ChainId): F[SignedTransaction] =
    SignedTransaction.sign[F](tx, keyPair, chainId)
}

object Wallet {
  def fromSecret[F[_]: Sync](secret: KeyPair.Secret): F[Wallet] =
    for {
      public <- Signature[ECDSA].generatePublicKey[F](secret)
      keyPair = KeyPair(public, secret)
      address = Address(keyPair)
    } yield Wallet(address, keyPair)
}
