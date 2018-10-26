package jbok.core.models

import jbok.codec.json._
import jbok.codec.rlp.codecs._
import jbok.crypto._
import jbok.crypto.authds.mpt.MPTrie
import scodec.bits.ByteVector

case class Account(
    nonce: UInt256 = 0,
    balance: UInt256 = 0,
    storageRoot: ByteVector = Account.EmptyStorageRootHash,
    codeHash: ByteVector = Account.EmptyCodeHash
) {
  def increaseBalance(value: UInt256): Account =
    copy(balance = balance + value)

  def increaseNonce(value: UInt256 = 1): Account =
    copy(nonce = nonce + value)

  def withCode(codeHash: ByteVector): Account =
    copy(codeHash = codeHash)

  def withStorage(storageRoot: ByteVector): Account =
    copy(storageRoot = storageRoot)

  /**
    * According to EIP161: An account is considered empty when it has no code and zero nonce and zero balance.
    * An account's storage is not relevant when determining emptiness.
    */
  def isEmpty(startNonce: UInt256 = UInt256.Zero): Boolean =
    nonce == startNonce && balance == UInt256.Zero && codeHash == Account.EmptyCodeHash

  /**
    * Under EIP-684 if this evaluates to true then we have a conflict when creating a new account
    */
  def nonEmptyCodeOrNonce(startNonce: UInt256 = UInt256.Zero): Boolean =
    nonce != startNonce || codeHash != Account.EmptyCodeHash
}

object Account {
  val EmptyStorageRootHash: ByteVector = MPTrie.emptyRootHash

  val EmptyCodeHash: ByteVector = ByteVector.empty.kec256

  def empty(startNonce: UInt256 = UInt256.Zero): Account =
    Account(nonce = startNonce, storageRoot = EmptyStorageRootHash, codeHash = EmptyCodeHash)
}
