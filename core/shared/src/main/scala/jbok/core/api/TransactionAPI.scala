package jbok.core.api

import jbok.core.models.{Receipt, SignedTransaction}
import scodec.bits.ByteVector

trait TransactionAPI[F[_]] {
  def getTx(hash: ByteVector): F[Option[SignedTransaction]]

  def getPendingTx(hash: ByteVector): F[Option[SignedTransaction]]

  def getReceipt(hash: ByteVector): F[Option[Receipt]]

  def getTxByBlockHashAndIndex(hash: ByteVector, index: Int): F[Option[SignedTransaction]]

  def getTxByBlockTagAndIndex(tag: BlockTag, index: Int): F[Option[SignedTransaction]]

  def sendTx(stx: SignedTransaction): F[ByteVector]

  def sendRawTx(data: ByteVector): F[ByteVector]
}
