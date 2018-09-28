package jbok.core.mining

import jbok.core.config.GenesisConfig
import jbok.core.models.{Address, SignedTransaction, Transaction}
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef._
import scalax.collection.mutable.{Graph => MGraph}
import scodec.bits.ByteVector

import scala.annotation.tailrec
import scala.collection.mutable.{Map => MMap}
import scala.util.{Random, Try}

case class SimAddress(keyPair: KeyPair) {
  val address: Address = Address(keyPair)
}

case class SimAccount(balance: BigInt, nonce: BigInt) {
  def nonceIncreased: SimAccount = this.copy(nonce = this.nonce + 1)

  def balanceChanged(delta: BigInt): SimAccount = this.copy(balance = this.balance + delta)
}

class TxGen(nAddr: Int, gasLimit: BigInt = BigInt(21000)) {
  val initBalance = BigInt("1000000000000000000000000000000")

  val addresses = (1 to nAddr)
    .map(_ => {
      val keyPair = SecP256k1.generateKeyPair().unsafeRunSync()
      SimAddress(keyPair)
    })
    .toVector

  val alloc: Map[String, BigInt] = addresses.map(x => x.address.toString -> initBalance).toMap

  val genesisConfig = GenesisConfig.default.copy(alloc = alloc)

  val addressMap: Map[Address, Int] = addresses.map(_.address).zipWithIndex.toMap

  val accountMap: MMap[Address, SimAccount] =
    MMap.apply(addresses.map(x => x.address -> SimAccount(initBalance, 0)): _*)

  val lastTx: MMap[Address, SignedTransaction] = MMap.empty

  val gasPrice: BigInt = BigInt("1")

  def genValue(account: SimAccount): BigInt =
    BigInt("10000")

  @tailrec
  final def nextTxs(nTx: Int, g: MGraph[SignedTransaction, DiEdge] = MGraph.empty): List[SignedTransaction] =
    if (nTx <= 0) {
      g.topologicalSort.right.get.toList.map(_.toOuter)
    } else {
      val Vector(sender, receiver) = Random.shuffle(addresses).take(2)
      val senderAccount            = accountMap(sender.address)
      val receiverAccount          = accountMap(receiver.address)
      val value                    = genValue(senderAccount)
      accountMap += (sender.address   -> senderAccount.balanceChanged(-value).nonceIncreased)
      accountMap += (receiver.address -> receiverAccount.balanceChanged(value))
      val tx = Transaction(senderAccount.nonce,
                           gasPrice,
                           gasLimit,
                           receiver.address,
                           genValue(senderAccount),
                           ByteVector.empty)
      val stx = SignedTransaction.sign(tx, sender.keyPair)
      val spOpt = lastTx.get(sender.address) match {
        case Some(sp) => if (Try(g.get(sp)).isSuccess) Some(sp) else None
        case None     => None
      }
      val opOpt = lastTx.get(receiver.address) match {
        case Some(op) => if (Try(g.get(op)).isSuccess) Some(op) else None
        case None     => None
      }

      (spOpt, opOpt) match {
        case (Some(sp), Some(op)) =>
          g += (sp ~> stx, op ~> stx)
        case (Some(sp), _) =>
          g += (sp ~> stx)
        case (_, Some(op)) =>
          g += (op ~> stx)
        case _ =>
          g += stx
      }

      lastTx += (sender.address -> stx)
      nextTxs(nTx - 1, g)
    }
}
