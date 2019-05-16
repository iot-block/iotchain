package jbok.core.api

import jbok.core.models.{Account, Address, SignedTransaction}
import scodec.bits.ByteVector

trait AccountAPI[F[_]] {
  def getCode(address: Address, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getAccount(address: Address, tag: BlockTag = BlockTag.latest): F[Account]

  def getBalance(address: Address, tag: BlockTag = BlockTag.latest): F[BigInt]

  def getStorageAt(address: Address, position: BigInt, tag: BlockTag = BlockTag.latest): F[ByteVector]

  def getTransactions(address: Address): F[List[SignedTransaction]]

  def getTokenTransactions(address: Address, contract: Option[Address]): F[List[SignedTransaction]]

  def getPendingTxs(address: Address): F[List[SignedTransaction]]

  def getEstimatedNonce(address: Address): F[BigInt]
}
