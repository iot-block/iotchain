package jbok.app.service

import cats.effect.Sync
import cats.implicits._
import jbok.core.api.PersonalAPI
import jbok.core.config.HistoryConfig
import jbok.core.keystore.KeyStore
import jbok.core.ledger.History
import jbok.core.models.{Address, Transaction}
import jbok.core.pool.TxPool
import scodec.bits.ByteVector

import scala.util.Try

final class PersonalService[F[_]](
    historyConfig: HistoryConfig,
    keyStore: KeyStore[F],
    history: History[F],
    txPool: TxPool[F]
)(implicit F: Sync[F]) extends PersonalAPI[F] {

  override def importRawKey(privateKey: ByteVector, passphrase: String): F[Address] =
    keyStore.importPrivateKey(privateKey, passphrase)

  override def newAccount(passphrase: String): F[Address] =
    keyStore.newAccount(passphrase)

  override def delAccount(address: Address): F[Boolean] =
    keyStore.deleteAccount(address)

  override def listAccounts: F[List[Address]] =
    keyStore.listAccounts

  override def sendTransaction(
      from: Address,
      passphrase: String,
      to: Option[Address],
      value: Option[BigInt],
      gasLimit: Option[BigInt],
      gasPrice: Option[BigInt],
      nonce: Option[BigInt],
      data: Option[ByteVector],
  ): F[ByteVector] = {

    val defaultGasPrice: BigInt = 2 * BigInt(10).pow(10)
    val defaultGasLimit: BigInt = 90000

    implicit val chainId: BigInt = history.chainId

    for {
      wallet  <- keyStore.unlockAccount(from, passphrase)
      pending <- txPool.getPendingTransactions
      latestNonceOpt = Try(pending.collect {
        case (stx, _) if stx.senderAddress.contains(wallet.address) => stx.nonce
      }.max).toOption
      bn              <- history.getBestBlockNumber
      currentNonceOpt <- history.getAccount(from, bn).map(_.map(_.nonce.toBigInt))
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
      tx = Transaction(
        nonce = nonce.getOrElse(maybeNextTxNonce.getOrElse(historyConfig.accountStartNonce)),
        gasPrice = gasPrice.getOrElse(defaultGasPrice),
        gasLimit = gasLimit.getOrElse(defaultGasLimit),
        receivingAddress = to,
        value = value.getOrElse(BigInt(0)),
        payload = data.getOrElse(ByteVector.empty)
      )
      stx <- wallet.signTx[F](tx)
      _   <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
  }

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean] =
    keyStore.changePassphrase(address, oldPassphrase, newPassphrase)
}
