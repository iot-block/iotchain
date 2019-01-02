package jbok.core.mining

import cats.effect.IO
import cats.effect.concurrent.Ref
import fs2._
import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import scodec.bits.ByteVector

import scala.util.Random

final case class SimAccount(keyPair: KeyPair, balance: BigInt, nonce: BigInt) {
  val address: Address = Address(keyPair)

  def nonceIncreased: SimAccount = this.copy(nonce = this.nonce + 1)

  def balanceChanged(delta: BigInt): SimAccount = this.copy(balance = this.balance + delta)
}

class TxGenerator(val accounts: Ref[IO, Map[Address, SimAccount]])(implicit chainId: BigInt) {
  def newAccount: IO[SimAccount] = Signature[ECDSA].generateKeyPair[IO]().map(kp => SimAccount(kp, 0, 0))

  def genValue(sender: SimAccount, receiver: SimAccount): BigInt =
    BigInt("1000000")

  val gasPrice: BigInt = BigInt(1)

  val gasLimit: BigInt = BigInt(21000)

  def genTx: IO[SignedTransaction] =
    for {
      acc <- accounts.get
      List(sender) = Random.shuffle(acc.values.toList)
      receiver <- newAccount
      value = genValue(sender, receiver)
      _ <- accounts.update(_ ++ Map(sender.address -> sender.balanceChanged(-value).nonceIncreased))
//                 receiver.address -> receiver.balanceChanged(value)))
      tx = Transaction(
        sender.nonce,
        gasPrice,
        gasLimit,
        Some(receiver.address),
        value,
        ByteVector.empty
      )
      stx <- SignedTransaction.sign[IO](tx, sender.keyPair)
    } yield stx

  def genTxs: Stream[IO, SignedTransaction] =
    Stream.repeatEval(genTx)
}

object TxGenerator {
  def apply(miner: SimAccount)(implicit chainId: BigInt): IO[TxGenerator] =
    for {
      accounts <- Ref.of[IO, Map[Address, SimAccount]](Map(miner.address -> miner))
    } yield new TxGenerator(accounts)
}
