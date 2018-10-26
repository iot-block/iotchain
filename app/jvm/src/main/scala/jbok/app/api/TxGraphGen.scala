package jbok.app.simulations

import jbok.common.GraphUtil
import jbok.core.config.GenesisConfig
import jbok.core.models._
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef._
import scalax.collection._
import scalax.collection.io.dot.DotAttr
import scalax.collection.io.dot.implicits._
import scalax.collection.mutable.{Graph => MGraph}
import scodec.bits.ByteVector

import scala.collection.mutable.{ListBuffer => MList, Map => MMap}
import scala.util.Random

class TxGraphGen(nAddr: Int = 3, gasLimit: BigInt = BigInt(21000)) {
  case class SimTransaction(id: Int, sender: Address, receiver: Address)

  object SimTransaction {
    def empty: SimTransaction = SimTransaction(0, Address.empty, Address.empty)
  }

  case class SimAddress(keyPair: KeyPair) {
    def address = Address(keyPair)
  }

  val initBalance = BigInt("1000000000000000000000000000000")

  val keyPairs = (1 to nAddr)
    .map(_ => {
      val keyPair = SecP256k1.generateKeyPair().unsafeRunSync()
      SimAddress(keyPair)
    })
    .toVector

  val alloc: Map[String, BigInt] = keyPairs.map(x => x.address.toString -> initBalance).toMap

  val genesisConfig = GenesisConfig.default.copy(alloc = alloc)

  val keyPairMap: Map[Address, KeyPair] = keyPairs.map(x => x.address -> x.keyPair).toMap

  val accountMap: MMap[Address, Account] =
    MMap.apply(keyPairs.map(x => x.address -> Account(balance = UInt256(initBalance))): _*)

  val lastTx: MMap[Address, SimTransaction] = MMap.empty

  val gasPrice: BigInt = BigInt("1")

  def genValue(account: Account): BigInt =
    BigInt("10000")

  def genTransaction(account: Account, receiver: Address): (Transaction, Account) = {
    val value = genValue(account)
    (Transaction(account.nonce, gasPrice, gasLimit, receiver, value, ByteVector.empty),
     account.increaseNonce().increaseBalance(UInt256(-value)))
  }

  def genDAG(size: Int,
             g: MGraph[SimTransaction, DiEdge] = MGraph.empty,
             lastTransaction: Map[Address, SimTransaction] = Map.empty): Graph[SimTransaction, DiEdge] =
    if (size <= 0) {
      g
    } else if (g.order == 0) {
      genDAG(size, g + SimTransaction.empty, lastTransaction)
    } else {
      val Vector(sender, receiver) =
        Random.shuffle(keyPairs).take(2).map(_.address)
      val newTransaction = SimTransaction(size, sender, receiver)
      val senderLastTx   = lastTransaction.getOrElse(sender, SimTransaction.empty)
      val receiverLastTx = lastTransaction.getOrElse(receiver, SimTransaction.empty)
      g += (senderLastTx ~> newTransaction, receiverLastTx ~> newTransaction)
      genDAG(size - 1, g, lastTransaction + (sender -> newTransaction))
    }

  def dot(graph: Graph[SimTransaction, DiEdge]) = GraphUtil.graphviz(
    graph,
    (x: SimTransaction) => {
      List(
        DotAttr("label", s"Tx${x.id}-${x.sender.toString.take(4)}")
      )
    }
  )

  def genDoubleSpendDAG(size: Int,
                        g: MGraph[SimTransaction, DiEdge] = MGraph.empty,
                        lastTransaction: Map[Address, SimTransaction] = Map.empty): Graph[SimTransaction, DiEdge] =
    if (size < 2) {
      genDAG(size, g, lastTransaction)
    } else {
      val Vector(sender, receiver1, receiver2) =
        Random.shuffle(keyPairs).take(3).map(_.address)
      val newTransaction1 = SimTransaction(size, sender, receiver1)
      val newTransaction2 = SimTransaction(size - 1, sender, receiver2)
      val senderLastTx    = lastTransaction.getOrElse(sender, SimTransaction.empty)
      val receiver1LastTx = lastTransaction.getOrElse(receiver1, SimTransaction.empty)
      val receiver2LastTx = lastTransaction.getOrElse(receiver2, SimTransaction.empty)
      genDAG(
        size - 2,
        g + (senderLastTx ~> newTransaction1, receiver1LastTx ~> newTransaction1,
        senderLastTx ~> newTransaction2, receiver2LastTx ~> newTransaction2),
        lastTransaction + (sender -> newTransaction2)
      )
    }

  // require graph is dag
  def genTransactionFromDAG(g: Graph[SimTransaction, DiEdge],
                            accounts: Map[Address, Account],
                            keyPairMap: Map[Address, KeyPair]): (List[SignedTransaction], Map[Address, Account]) =
    if (g.isCyclic)
      (List(), accounts)
    else {
      val transactions                       = MList[SignedTransaction]()
      val mg: MGraph[SimTransaction, DiEdge] = MGraph.empty
      g.edges.map(mg += _)
      val mAccount: MMap[Address, Account] = MMap.empty

      def loop(mg: MGraph[SimTransaction, DiEdge], accounts: Map[Address, Account]): Map[Address, Account] =
        if (mg.order > 0) {
          val nextNodes = mg.nodes.filter(_.diPredecessors.isEmpty).toList.sortBy(-_.id)
          for {
            node <- nextNodes
            if accounts.contains(node.sender)
            (transaction, account) = genTransaction(accounts(node.sender), node.receiver)
            _                      = transactions += SignedTransaction.sign(transaction, keyPairMap(node.sender))
            _                      = mAccount += (node.sender -> account)
          } ()
          nextNodes.map(mg.remove(_))
          loop(mg, mAccount.foldLeft(accounts)((accounts, account) => accounts + account))
        } else {
          accounts
        }

      loop(mg, accounts)
      (transactions.toList, mAccount.toMap)
    }

  def nextValidTxs(nTx: Int): List[SignedTransaction] = {
    val graph            = genDAG(nTx, lastTransaction = lastTx.toMap)
    val (stx, mAccounts) = genTransactionFromDAG(graph, accountMap.toMap, keyPairMap)
    mAccounts.map(accountMap += _)
    // apply lastTransaction to lastTx
    stx
  }

  def nextDoubleSpendTxs(nTx: Int): List[SignedTransaction] = {
    val graph            = genDoubleSpendDAG(nTx, lastTransaction = lastTx.toMap)
    val (stx, mAccounts) = genTransactionFromDAG(graph, accountMap.toMap, keyPairMap)
    stx
  }

  def nextDoubleSpendTxs2(nTx: Int): List[SignedTransaction] = {
    val graph              = genDAG(nTx, lastTransaction = lastTx.toMap)
    val txAccounts         = graph.nodes.map(_.sender).toSet.toVector
    val doubleSpendAccount = Random.shuffle(txAccounts).take(1).head
    val account            = accountMap(doubleSpendAccount)
    val (stx, mAccounts) = genTransactionFromDAG(
      graph,
      accountMap.toMap + (doubleSpendAccount -> account.copy(nonce = account.nonce - 1)),
      keyPairMap)
    stx
  }

  def getCoin(address: Address, value: BigInt): SignedTransaction = {
    val keyPair = Random.shuffle(keyPairs).head
    val sender  = keyPair.address
    val tx      = Transaction(accountMap(sender).nonce, gasPrice, gasLimit, address, value, ByteVector.empty)
    accountMap(sender).increaseNonce().increaseBalance(UInt256(-value))
    SignedTransaction.sign(tx, keyPair.keyPair)
  }
}
