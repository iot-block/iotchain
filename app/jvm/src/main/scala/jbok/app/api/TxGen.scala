package jbok.app.api

import cats.effect.IO
import fs2._
import jbok.core.models.{Account, Address, SignedTransaction, Transaction}
import jbok.crypto.signature.KeyPair
import scodec.bits.ByteVector

import scala.collection.mutable.{Map => MMap}
import scala.util.Random

object TxGen {
  val value: BigInt = BigInt(100000)

  val gasPrice: BigInt = BigInt(1)

  val gasLimit: BigInt = BigInt(21000)

  def genTxs(nTx: Int, accounts: Map[KeyPair, Account], cId: BigInt): IO[List[SignedTransaction]] = {
    val simuAccountsMap: MMap[Address, SimAccount] = MMap(accounts.toList.map {
      case (keyPair, account) => Address(keyPair) -> SimAccount(keyPair, account.balance, account.nonce)
    }: _*)

    implicit val chainId: BigInt = cId

    def genTx: IO[SignedTransaction] =
      for {
        List(sender, receiver) <- IO(Random.shuffle(simuAccountsMap.values.toList).take(2))
        toValue = value.min(sender.balance)
        tx      = Transaction(sender.nonce, gasPrice, gasLimit, Some(receiver.address), toValue, ByteVector.empty)
        _       = simuAccountsMap += sender.address -> sender.nonceIncreased.balanceChanged(-toValue)
        _       = simuAccountsMap += receiver.address -> receiver.balanceChanged(toValue)
        stx <- SignedTransaction.sign[IO](tx, sender.keyPair)
      } yield stx

    Stream.repeatEval(genTx).take(nTx).compile.toList
  }
}

final case class SimAccount(keyPair: KeyPair, balance: BigInt, nonce: BigInt) {
  val address: Address = Address(keyPair)

  def nonceIncreased: SimAccount = this.copy(nonce = this.nonce + 1)

  def balanceChanged(delta: BigInt): SimAccount = this.copy(balance = this.balance + delta)
}
