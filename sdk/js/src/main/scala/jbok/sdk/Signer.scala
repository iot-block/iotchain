package jbok.sdk

import cats.effect.IO
import cats.implicits._
import jbok.core.models.{ChainId, SignedTransaction, Transaction}
import jbok.core.validators.TxValidator
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Signer")
@JSExportAll
object Signer {
  def getSender(tx: SignedTransaction): js.UndefOr[String] =
    tx.senderAddress.map(_.toString).orUndefined

  def signTx(tx: Transaction, secretHex: String, chainId: Int): SignedTransaction =
    (for {
      secret <- KeyPair.Secret(secretHex).pure[IO]
      public <- Signature[ECDSA].generatePublicKey[IO](secret)
      keyPair = KeyPair(public, secret)
      stx <- SignedTransaction.sign[IO](tx, keyPair, ChainId(chainId))
      _   <- TxValidator.checkSyntacticValidity[IO](stx, ChainId(chainId))
    } yield stx).unsafeRunSync()
}
