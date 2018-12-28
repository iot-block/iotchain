package jbok.sdk

import cats.effect.{IO, Sync}
import cats.implicits._
import jbok.core.models.{SignedTransaction, Transaction}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Signer")
@JSExportAll
object Signer {
  def getSender(tx: SignedTransaction): js.UndefOr[String] =
    tx.senderAddress.map(_.toString).orUndefined

  def signTx(tx: Transaction, secretHex: String, chainId: Int): js.UndefOr[SignedTransaction] =
    (for {
      secret <- KeyPair.Secret(secretHex).pure[IO]
      public <- Signature[ECDSA].generatePublicKey[IO](secret)
      keyPair = KeyPair(public, secret)
      stx <- SignedTransaction.sign[IO](tx, keyPair)(Sync[IO], chainId)
    } yield stx).attempt.unsafeRunSync().toOption.orUndefined
}
