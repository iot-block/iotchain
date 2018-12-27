package jbok.sdk

import jbok.core.models.SignedTransaction

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("Signer")
@JSExportAll
object Signer {
  def getSender(tx: SignedTransaction): js.UndefOr[String] =
    tx.senderAddress.map(_.toString).orUndefined
}
