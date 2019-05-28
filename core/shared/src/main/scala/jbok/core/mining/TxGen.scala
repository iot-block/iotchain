package jbok.core.mining

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.common.log.Logger
import jbok.core.api.JbokClient
import jbok.core.ledger.History
import jbok.core.mining.TxGen.SimAccount
import jbok.core.models._
import jbok.crypto.signature.KeyPair
import scodec.bits.ByteVector

import scala.util.Random

final class TxGen[F[_]] private (accounts: Ref[F, Map[Address, SimAccount]], readAccount: Address => F[Account])(implicit F: Sync[F], chainId: BigInt) {
  import TxGen._

  private[this] val log = Logger[F]

  def updateAccounts: F[Unit] =
    accounts.get.flatMap {
      _.toList.traverse_ {
        case (_, account) =>
          for {
            acc <- readAccount(account.address)
            _   <- accounts.update(_ + (account.address -> account.fromAccount(acc)))
          } yield ()
      }
    }

  def updateAccount(account: SimAccount): F[Unit] =
    accounts.update(_ + (account.address -> account))

  val gasPrice: BigInt = BigInt("1")

  val gasLimit: BigInt = BigInt(21000)

  val minBalance: BigInt = BigInt(100000)

  def genValue(account: SimAccount): BigInt =
    BigInt(25000)

  def getValidSenderReceiver: F[(SimAccount, SimAccount)] =
    for {
      accounts <- accounts.get.map(_.toList.map(_._2))
      available = accounts.filter(_.balance > minBalance)
      result <- if (available.isEmpty) {
        F.raiseError(new Exception(s"no available senders, all accounts balance <= ${minBalance}"))
      } else {
        val sender   = available(Random.nextInt(available.length))
        val receiver = accounts(Random.nextInt(accounts.length))
        F.pure(sender -> receiver)
      }
    } yield result

  def genExternalTx(sender: SimAccount, receiver: SimAccount): F[SignedTransaction] = {
    val value = genValue(sender)
    val tx = Transaction(
      sender.nonce,
      gasPrice,
      gasLimit,
      Some(receiver.address),
      value,
      ByteVector.empty
    )

    for {
      stx <- SignedTransaction.sign[F](tx, sender.keyPair)
      _   <- updateAccount(sender.balanceChanged(-value).nonceIncreased)
    } yield stx
  }

  def genValidExternalTx: F[SignedTransaction] =
    for {
      (sender, receiver) <- getValidSenderReceiver
      stx                <- genExternalTx(sender, receiver)
      _                  <- log.d(s"gen valid normal tx: from=${sender.address.toString.take(9)} nonce=${sender.nonce} to=${receiver.address.toString.take(9)} value=${stx.value / 1e18.toLong}")
    } yield stx

  def genValidExternalTxN(n: Int): F[List[SignedTransaction]] =
    updateAccounts >> genValidExternalTx.replicateA(n)

//  def genDAG(
//      size: Int,
//      g: MGraph[SimTransaction, DiEdge] = MGraph.empty,
//      lastTransaction: Map[Address, SimTransaction] = Map.empty
//  ): Graph[SimTransaction, DiEdge] =
//    if (size <= 0) {
//      g
//    } else if (g.order == 0) {
//      genDAG(size, g + SimTransaction.empty, lastTransaction)
//    } else {
//      val Vector(sender, receiver) =
//        Random.shuffle(keyPairs).take(2).map(_.address)
//      val newTransaction = SimTransaction(size, sender, receiver)
//      val senderLastTx   = lastTransaction.getOrElse(sender, SimTransaction.empty)
//      val receiverLastTx = lastTransaction.getOrElse(receiver, SimTransaction.empty)
//      g += (senderLastTx ~> newTransaction, receiverLastTx ~> newTransaction)
//      genDAG(size - 1, g, lastTransaction + (sender -> newTransaction))
//    }
//
//  def dot(graph: Graph[SimTransaction, DiEdge]) = GraphUtil.graphviz(
//    graph,
//    (x: SimTransaction) => {
//      List(
//        DotAttr("label", s"Tx${x.id}-${x.sender.toString.take(4)}")
//      )
//    }
//  )
//
//  def genDoubleSpendDAG(size: Int, g: MGraph[SimTransaction, DiEdge] = MGraph.empty, lastTransaction: Map[Address, SimTransaction] = Map.empty): Graph[SimTransaction, DiEdge] =
//    if (size < 2) {
//      genDAG(size, g, lastTransaction)
//    } else {
//      val Vector(sender, receiver1, receiver2) =
//        Random.shuffle(keyPairs).take(3).map(_.address)
//      val newTransaction1 = SimTransaction(size, sender, receiver1)
//      val newTransaction2 = SimTransaction(size - 1, sender, receiver2)
//      val senderLastTx    = lastTransaction.getOrElse(sender, SimTransaction.empty)
//      val receiver1LastTx = lastTransaction.getOrElse(receiver1, SimTransaction.empty)
//      val receiver2LastTx = lastTransaction.getOrElse(receiver2, SimTransaction.empty)
//      genDAG(
//        size - 2,
//        g + (senderLastTx ~> newTransaction1, receiver1LastTx ~> newTransaction1,
//        senderLastTx ~> newTransaction2, receiver2LastTx ~> newTransaction2),
//        lastTransaction + (sender -> newTransaction2)
//      )
//    }

  // require graph is dag
//  def genTransactionFromDAG(g: Graph[SimTransaction, DiEdge],
//                            accounts: Map[Address, Account],
//                            keyPairMap: Map[Address, KeyPair]): (List[SignedTransaction], Map[Address, Account]) =
//    if (g.isCyclic)
//      (List(), accounts)
//    else {
//      val transactions                       = MList[SignedTransaction]()
//      val mg: MGraph[SimTransaction, DiEdge] = MGraph.empty
//      g.edges.map(mg += _)
//      val mAccount: MMap[Address, Account] = MMap.empty
//
//      def loop(mg: MGraph[SimTransaction, DiEdge], accounts: Map[Address, Account]): Map[Address, Account] =
//        if (mg.order > 0) {
//          val nextNodes = mg.nodes.filter(_.diPredecessors.isEmpty).toList.sortBy(-_.id)
//          for {
//            node <- nextNodes
//            if accounts.contains(node.sender)
//            (transaction, account) = genTransaction(accounts(node.sender), node.receiver)
//            _                      = transactions += SignedTransaction.sign[IO](transaction, keyPairMap(node.sender)).unsafeRunSync()
//            _                      = mAccount += (node.sender -> account)
//          } ()
//          nextNodes.map(mg.remove(_))
//          loop(mg, mAccount.foldLeft(accounts)((accounts, account) => accounts + account))
//        } else {
//          accounts
//        }
//
//      loop(mg, accounts)
//      (transactions.toList, mAccount.toMap)
//    }
//
//  def nextValidTxs(nTx: Int): List[SignedTransaction] = {
//    val graph            = genDAG(nTx, lastTransaction = lastTx.toMap)
//    val (stx, mAccounts) = genTransactionFromDAG(graph, accountMap.toMap, keyPairMap)
//    mAccounts.map(accountMap += _)
//    // apply lastTransaction to lastTx
//    stx
//  }
//
//  def nextDoubleSpendTxs(nTx: Int): List[SignedTransaction] = {
//    val graph            = genDoubleSpendDAG(nTx, lastTransaction = lastTx.toMap)
//    val (stx, mAccounts) = genTransactionFromDAG(graph, accountMap.toMap, keyPairMap)
//    stx
//  }
//
//  def nextDoubleSpendTxs2(nTx: Int): List[SignedTransaction] = {
//    val graph              = genDAG(nTx, lastTransaction = lastTx.toMap)
//    val txAccounts         = graph.nodes.map(_.sender).toSet.toVector
//    val doubleSpendAccount = Random.shuffle(txAccounts).take(1).head
//    val account            = accountMap(doubleSpendAccount)
//    val (stx, mAccounts)   = genTransactionFromDAG(graph, accountMap.toMap + (doubleSpendAccount -> account.copy(nonce = account.nonce - 1)), keyPairMap)
//    stx
//  }
//
}

object TxGen {
  def apply[F[_]](keyPairs: List[KeyPair], readAccount: Address => F[Account])(implicit F: Sync[F], chainId: BigInt): F[TxGen[F]] =
    for {
      ref <- Ref.of[F, Map[Address, SimAccount]](Map.empty)
      _   <- ref.update(_ ++ keyPairs.map(kp => Address(kp) -> SimAccount(kp, 0, 0)))
      txGen = new TxGen[F](ref, readAccount)
      _ <- txGen.updateAccounts
    } yield txGen

  def apply[F[_]](keyPairs: List[KeyPair], history: History[F])(implicit F: Sync[F], chainId: BigInt): F[TxGen[F]] =
    apply(keyPairs, address => history.getBestBlockNumber.flatMap(n => history.getAccount(address, n).map(_.getOrElse(Account.empty()))))

  def apply[F[_]](keyPairs: List[KeyPair], client: JbokClient[F])(implicit F: Sync[F], chainId: BigInt): F[TxGen[F]] =
    apply(keyPairs, address => client.account.getAccount(address))

  final case class SimAccount(keyPair: KeyPair, balance: BigInt, nonce: BigInt) {
    val address: Address = Address(keyPair)

    def nonceIncreased: SimAccount = this.copy(nonce = this.nonce + 1)

    def balanceChanged(delta: BigInt): SimAccount = this.copy(balance = this.balance + delta)

    def fromAccount(account: Account): SimAccount =
      copy(nonce = account.nonce.toBigInt, balance = account.balance.toBigInt)
  }
}
