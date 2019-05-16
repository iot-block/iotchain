package jbok.core.mining

import cats.effect.Sync
import cats.implicits._
import fs2._
import jbok.core.models._
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector

import scala.collection.mutable.{Map => MMap}
import scala.util.Random

final case class SimAccount(keyPair: KeyPair, balance: BigInt, nonce: BigInt) {
  val address: Address = Address(keyPair)

  def nonceIncreased: SimAccount = this.copy(nonce = this.nonce + 1)

  def balanceChanged(delta: BigInt): SimAccount = this.copy(balance = this.balance + delta)
}

object TxGen {
  val value: BigInt = BigInt(100000)

  val gasPrice: BigInt = BigInt(1)

  val gasLimit: BigInt = BigInt(21000)

  def genTxs[F[_]](nTx: Int, accounts: Map[KeyPair, Account])(implicit F: Sync[F], chainId: BigInt): F[(Map[KeyPair, Account], List[SignedTransaction])] = {
    val simuAccountsMap: MMap[KeyPair, SimAccount] = MMap(accounts.toList.map {
      case (keyPair, account) => keyPair -> SimAccount(keyPair, account.balance, account.nonce)
    }: _*)

    val accountsMap: MMap[KeyPair, Account] = MMap(accounts.toSeq: _*)

    def genTxMutually: F[SignedTransaction] =
      for {
        List(sender, receiver) <- F.delay(Random.shuffle(simuAccountsMap.values.toList).take(2))
        toValue = value.min(sender.balance)
        tx      = Transaction(sender.nonce, gasPrice, gasLimit, Some(receiver.address), toValue, ByteVector.empty)
        _       = simuAccountsMap += sender.keyPair -> sender.nonceIncreased.balanceChanged(-toValue)
        _       = simuAccountsMap += receiver.keyPair -> receiver.balanceChanged(toValue)
        stx <- SignedTransaction.sign[F](tx, sender.keyPair)
      } yield stx

    def genTxRandomThrow: F[SignedTransaction] =
      for {
        List(sender) <- F.delay(Random.shuffle(simuAccountsMap.values.toList))
        receiver     <- Signature[ECDSA].generateKeyPair[F]().map(Address.apply)
        _  = simuAccountsMap += sender.keyPair -> sender.nonceIncreased.balanceChanged(-value)
        tx = Transaction(sender.nonce, gasPrice, gasLimit, Some(receiver), value, ByteVector.empty)
        stx <- SignedTransaction.sign[F](tx, sender.keyPair)
      } yield stx

    val signedTransactions: F[List[SignedTransaction]] =
      if (accounts.size == 1)
        Stream.repeatEval(genTxRandomThrow).take(nTx).compile.toList
      else if (accounts.size > 2)
        Stream.repeatEval(genTxMutually).take(nTx).compile.toList
      else
        F.raiseError(new Exception("no account to generate txs."))

    signedTransactions.map(stxs => simuAccountsMap.mapValues(sa => Account(UInt256(sa.nonce), UInt256(sa.balance))).toMap -> stxs)
  }
}
