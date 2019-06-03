package jbok.app.service

import cats.effect.Sync
import cats.implicits._
import jbok.common.math.N
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
)(implicit F: Sync[F])
    extends PersonalAPI[F] {

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
      value: Option[N],
      gasLimit: Option[N],
      gasPrice: Option[N],
      nonce: Option[N],
      data: Option[ByteVector]
  ): F[ByteVector] = {

    val defaultGasPrice: N = 2 * N(10).pow(10)
    val defaultGasLimit: N = N(90000)

    for {
      wallet  <- keyStore.unlockAccount(from, passphrase)
      pending <- txPool.getPendingTransactions
      latestNonceOpt = Try(pending.collect {
        case (stx, _) if stx.senderAddress.contains(wallet.address) => stx.nonce
      }.max).toOption
      bn              <- history.getBestBlockNumber
      currentNonceOpt <- history.getAccount(from, bn).map(_.map(_.nonce.toN))
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
      tx = Transaction(
        nonce = nonce.getOrElse(maybeNextTxNonce.getOrElse(historyConfig.accountStartNonce)),
        gasPrice = gasPrice.getOrElse(defaultGasPrice),
        gasLimit = gasLimit.getOrElse(defaultGasLimit),
        receivingAddress = to,
        value = value.getOrElse(N(0)),
        payload = data.getOrElse(ByteVector.empty)
      )
      stx <- wallet.signTx[F](tx, history.chainId)
      _   <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
  }

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean] =
    keyStore.changePassphrase(address, oldPassphrase, newPassphrase)
}
