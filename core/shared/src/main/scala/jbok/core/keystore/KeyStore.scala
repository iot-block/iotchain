package jbok.core.keystore

import jbok.core.models.Address
import scodec.bits.ByteVector

object KeyStoreError {
  case object KeyNotFound         extends Exception("KeyNotFound")
  case object DecryptionFailed    extends Exception("DecryptionFailed")
  case object InvalidKeyFormat    extends Exception("InvalidKeyFormat")
  case class IOError(msg: String) extends Exception(s"IO error, ${msg}")
}

trait KeyStore[F[_]] {
  def newAccount(passphrase: String): F[Address]

  def importPrivateKey(key: ByteVector, passphrase: String): F[Address]

  def listAccounts: F[List[Address]]

  def unlockAccount(address: Address, passphrase: String): F[Wallet]

  def deleteWallet(address: Address): F[Boolean]

  def changePassphrase(
      address: Address,
      oldPassphrase: String,
      newPassphrase: String
  ): F[Boolean]

  def clear: F[Boolean]
}