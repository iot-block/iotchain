package jbok.app.service

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.core.config.Configs.HistoryConfig
import jbok.core.keystore.{KeyStore, Wallet}
import jbok.core.ledger.History
import jbok.core.models.{Address, SignedTransaction}
import jbok.core.pool.TxPool
import jbok.crypto._
import jbok.crypto.signature._
import jbok.sdk.api.{BlockParam, PersonalAPI, TransactionRequest}
import scodec.bits.ByteVector

import scala.concurrent.duration.Duration
import scala.util.Try

final class PersonalService[F[_]](
    historyConfig: HistoryConfig,
    keyStore: KeyStore[F],
    history: History[F],
    txPool: TxPool[F],
    unlockedWallets: Ref[F, Map[Address, Wallet]]
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

  override def unlockAccount(address: Address, passphrase: String, duration: Option[Duration]): F[Boolean] =
    for {
      wallet <- keyStore.unlockAccount(address, passphrase)
      _      <- unlockedWallets.update(_ + (address -> wallet))
    } yield true

  override def lockAccount(address: Address): F[Boolean] =
    unlockedWallets.update(_ - address).map(_ => true)

  override def sign(message: ByteVector, address: Address, passphrase: Option[String]): F[CryptoSignature] =
    for {
      wallet <- passphrase.fold(unlockedWallets.get.map(_(address)))(p => keyStore.unlockAccount(address, p))
      sig    <- Signature[ECDSA].sign[F](getMessageToSign(message).toArray, wallet.keyPair, history.chainId)
    } yield sig

  override def ecRecover(message: ByteVector, signature: CryptoSignature): F[Address] = F.fromOption(
    Signature[ECDSA]
      .recoverPublic(getMessageToSign(message).toArray, signature, history.chainId)
      .map(public => Address(public.bytes.kec256)),
    new Exception(s"unable to recover signing address")
  )

  override def sendTransaction(tx: TransactionRequest, passphrase: Option[String]): F[ByteVector] =
    passphrase match {
      case Some(p) =>
        for {
          wallet <- keyStore.unlockAccount(tx.from, p)
          hash   <- sendTransaction(tx, wallet)
        } yield hash

      case None =>
        unlockedWallets.get.map(_.get(tx.from)).flatMap {
          case Some(wallet) =>
            sendTransaction(tx, wallet)
          case None =>
            F.raiseError(new Exception("invalidRequest"))
        }
    }

  override def deleteWallet(address: Address): F[Boolean] =
    for {
      _ <- unlockedWallets.update(_ - address)
      r <- keyStore.deleteAccount(address)
    } yield r

  override def changePassphrase(address: Address, oldPassphrase: String, newPassphrase: String): F[Boolean] =
    keyStore.changePassphrase(address, oldPassphrase, newPassphrase)

  override def getAccountTransactions(address: Address, fromBlock: BlockParam, toBlock: BlockParam): F[List[SignedTransaction]] = {
    def collectTxs: PartialFunction[SignedTransaction, SignedTransaction] = {
      case stx if stx.senderAddress.contains(address) => stx
      case stx if stx.receivingAddress == address     => stx
    }

    def resolveNumber(param: BlockParam): F[BigInt] = param match {
      case BlockParam.Earliest      => BigInt(0).pure[F]
      case BlockParam.Latest        => history.getBestBlockNumber
      case BlockParam.WithNumber(n) => n.pure[F]
    }

    for {
      sn     <- resolveNumber(fromBlock)
      en     <- resolveNumber(toBlock)
      blocks <- (sn to en).toList.filter(_ >= BigInt(0)).traverse(history.getBlockByNumber)
      stxsFromBlock = blocks.collect {
        case Some(block) => block.body.transactionList.collect(collectTxs)
      }.flatten
      pendingStxs <- txPool.getPendingTransactions
      stxsFromPool = pendingStxs.keys.toList.collect(collectTxs)
    } yield stxsFromBlock ++ stxsFromPool
  }

  private[jbok] def getMessageToSign(message: ByteVector) = {
    val prefixed: Array[Byte] =
      0x19.toByte +: s"Ethereum Signed Message:\n${message.length}".getBytes ++: message.toArray
    ByteVector(prefixed.kec256)
  }

  private[jbok] def sendTransaction(request: TransactionRequest, wallet: Wallet): F[ByteVector] = {
    implicit val chainId: BigInt = history.chainId
    for {
      pending <- txPool.getPendingTransactions
      latestNonceOpt = Try(pending.collect {
        case (stx, _) if stx.senderAddress.contains(wallet.address) => stx.nonce
      }.max).toOption
      bn              <- history.getBestBlockNumber
      currentNonceOpt <- history.getAccount(request.from, bn).map(_.map(_.nonce.toBigInt))
      maybeNextTxNonce = latestNonceOpt.map(_ + 1).orElse(currentNonceOpt)
      tx               = request.toTransaction(maybeNextTxNonce.getOrElse(historyConfig.accountStartNonce))

      stx <- wallet.signTx[F](tx)
      _   <- txPool.addOrUpdateTransaction(stx)
    } yield stx.hash
  }

}
