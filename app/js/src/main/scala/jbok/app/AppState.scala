package jbok.app

import cats.effect._
import cats.implicits._
import com.thoughtworks.binding.Binding.{Var, Vars}
import fs2.Stream
import jbok.app.execution._
import jbok.common.log.Logger
import jbok.core.api.{BlockTag, JbokClient}
import jbok.core.models._
import jbok.evm.solidity.ABIDescription.FunctionDescription
import scodec.bits.ByteVector

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object execution {
  implicit val EC: ExecutionContext = ExecutionContext.Implicits.global

  implicit val T: Timer[IO] = IO.timer(EC)

  implicit val C: Clock[IO] = T.clock

  implicit val CS: ContextShift[IO] = IO.contextShift(EC)

  implicit val CE: ConcurrentEffect[IO] = IO.ioConcurrentEffect(CS)
}

final case class ClientStatus(number: Var[BigInt] = Var(0), gasPrice: Var[BigInt] = Var(0), gasLimit: Var[BigInt] = Var(0), miningStatus: Var[String] = Var("idle"))

final case class BlockHistory(history: Vars[Block] = Vars.empty) {
  def bestBlockNumber(): Int = history.value.lastOption.map(_.header.number.toInt).getOrElse(-1)
}

final case class Contract(address: Address, abi: List[FunctionDescription])

final case class NodeData(
    id: String,
    interface: String,
    port: Int,
    status: ClientStatus = ClientStatus(),
    blocks: BlockHistory = BlockHistory(),
    addresses: Var[Map[Address, Option[Account]]] = Var(Map.empty),
    stxs: Vars[SignedTransaction] = Vars.empty[SignedTransaction],
    receipts: Var[Map[ByteVector, Var[Option[Receipt]]]] = Var(Map.empty),
    contractsABI: Var[Map[Address, Contract]] = Var(Map.empty),
    contracts: Var[Map[Address, Option[Account]]] = Var(Map.empty)
) {
  lazy val addr = s"http://$interface:$port"
}

final case class Search(
    blockParam: BlockTag,
    searchType: String,
    keyword: String
)

final case class Loading(
    loadingStatus: Var[Boolean] = Var(false),
    loadingAddresses: Var[Boolean] = Var(false),
    loadingAccounts: Var[Boolean] = Var(false),
    loadingContracts: Var[Boolean] = Var(false),
    loadingReceipts: Var[Boolean] = Var(false),
    loadingBlocks: Var[Boolean] = Var(false)
) {
  def setAll(value: Boolean): Unit = {
    loadingStatus.value = value
    loadingAddresses.value = value
    loadingAccounts.value = value
    loadingContracts.value = value
    loadingReceipts.value = value
    loadingBlocks.value = value
  }
}

final case class AppState(
    config: Var[AppConfig],
    activeNode: Var[Option[String]] = Var(None),
    nodes: Var[Map[String, NodeData]] = Var(Map.empty),
    search: Var[Option[Search]] = Var(None),
    clients: Var[Map[String, JbokClient[IO]]] = Var(Map.empty),
    simuAddress: Vars[Address] = Vars.empty[Address],
    isLoading: Loading = Loading(),
    activeSearchViewFunc: Var[() => Unit] = Var(() => ()),
    update: Var[Boolean] = Var(true)
) {
  private val log = Logger[IO]

  def init() = {
    val p = for {
      _ <- IO.delay(addNode(config.value.port.toString, config.value.interface, config.value.port))
      _ = addClient(config.value.port.toString, config.value.url)
      _ <- updateTask()
      _ <- stream.compile.drain
    } yield ()

    p.unsafeToFuture()
  }

  def clearSeletedNode(): Unit = {
    activeNode.value = None
    isLoading.setAll(false)
  }

  def selectNode(idOpt: Option[String]): Unit = {
    activeNode.value = idOpt
    idOpt.foreach { _ =>
      isLoading.setAll(true)
    }
  }

  def addNode(id: String, interface: String, port: Int): Unit =
    if (!nodes.value.contains(id)) {
      nodes.value += id -> NodeData(id, interface, port)
      if (activeNode.value === None) {
        selectNode(Some(id))
      } else {
        None
      }
    } else {}

  def activeSearchView(f: () => Unit): Unit =
    activeSearchViewFunc.value = f

  def addClient(id: String, url: String): Unit =
    clients.value += id -> JbokClientPlatform[IO](url)

  def addStx(id: String, signedTransaction: SignedTransaction): Unit =
    nodes.value.get(id).foreach { node =>
      node.stxs.value += signedTransaction
      node.receipts.value += signedTransaction.hash -> Var(None)
    }

  def addContract(id: String, address: Address): Unit =
    nodes.value.get(id).foreach { node =>
      node.contracts.value += address -> None
    }

  def searchTxHash(hash: String): Unit =
    search.value = Some(Search(BlockTag.latest, "txHash", hash))

  def searchBlockHash(hash: String): Unit = {
    activeSearchViewFunc.value.apply()
    search.value = Some(Search(BlockTag.latest, "blockHash", hash))
  }

  def searchBlockNumber(number: String): Unit = {
    activeSearchViewFunc.value.apply()
    search.value = Some(Search(BlockTag.latest, "blockNumber", number))
  }

  def searchAccount(account: String): Unit = {
    activeSearchViewFunc.value.apply()
    search.value = Some(Search(BlockTag.latest, "account", account))
  }

  def clearSearch(): Unit =
    search.value = None

  def updateStatus(status: ClientStatus, client: JbokClient[IO]): IO[Unit] =
    for {
      bestBlockNumber <- client.block.getBestBlockNumber
      block           <- client.block.getBlockByNumber(bestBlockNumber)
      isMining = true
      gasPrice <- client.contract.getGasPrice
      miningStatus = if (isMining) "Mining" else "idle"
      gasLimit     = block.map(_.header.gasLimit).getOrElse(BigInt(0))
      _            = status.number.value = bestBlockNumber
      _            = status.miningStatus.value = miningStatus
      _            = status.gasPrice.value = gasPrice
      _            = status.gasLimit.value = gasLimit
    } yield ()

  def updateBlocks(blockHistory: BlockHistory, client: JbokClient[IO]): IO[Unit] =
    for {
      bestBlockNumber <- client.block.getBestBlockNumber
      localBestBlockNumber = blockHistory.bestBlockNumber()
      expire               = bestBlockNumber - config.value.blockHistorySize
      start                = (localBestBlockNumber + 1).max(expire.toInt)
      xs <- (start.toInt to bestBlockNumber.toInt)
        .map(BigInt(_))
        .toList
        .traverse(client.block.getBlockByNumber)
      _ = blockHistory.history.value --= blockHistory.history.value.filter(_.header.number < expire)
      _ = blockHistory.history.value ++= xs.flatten
    } yield ()

  def updateAddresses(addresses: Var[Map[Address, Option[Account]]], client: JbokClient[IO]): IO[Unit] =
    for {
      newAddr <- client.personal.listAccounts
      _ = newAddr.foreach { address =>
        if (!addresses.value.contains(address)) {
          addresses.value += address -> None
        } else {}
      }
    } yield ()

  def updateAccounts(addresses: Var[Map[Address, Option[Account]]], client: JbokClient[IO]): IO[Unit] = {
    val addrs = addresses.value.keys.toList
    for {
      simuAccounts <- addrs.traverse[IO, Account](addr => client.account.getAccount(addr, BlockTag.latest))
      _            <- log.i(simuAccounts.map(_.balance).mkString("\n"))
      _ = addresses.value ++= addrs.zip(simuAccounts.map(Some(_)))
    } yield ()
  }

  def updateReceipts(stxs: Vars[SignedTransaction], receipts: Var[Map[ByteVector, Var[Option[Receipt]]]], client: JbokClient[IO]): IO[Unit] = {
    val txHashes = stxs.value.map(_.hash).toList
    for {
      txReceipts <- txHashes.traverse[IO, Option[Receipt]](client.transaction.getReceipt)
      _ = receipts.value ++= txHashes.zip(txReceipts.map(Var(_))).toMap
    } yield ()
  }

  def updateContracts(contracts: Var[Map[Address, Option[Account]]], contractsABI: Var[Map[Address, Contract]], client: JbokClient[IO]): IO[Unit] = {
    val addresses = (contracts.value.keys.toSet ++ contractsABI.value.keys.toSet).toList
    for {
      simuAccounts <- addresses.traverse[IO, Account](addr => client.account.getAccount(addr, BlockTag.latest))
      _ = contracts.value ++= addresses.zip(simuAccounts.map(Some(_)))
    } yield ()
  }

  def updateTask(): IO[Unit] =
    if (update.value) {
      (for {
        currId <- activeNode.value
        node   <- nodes.value.get(currId)
//        client <- clients.value.get(node.id)
        client <- JbokClientPlatform.apply[IO](s"http://${node.interface}:${node.port}").some
      } yield node -> client)
        .map {
          case (node, client) =>
            for {
              _ <- updateStatus(node.status, client) *> IO(isLoading.loadingStatus.value = false)
              _ <- updateAddresses(node.addresses, client) *> IO(isLoading.loadingAddresses.value = false)
              _ <- updateAccounts(node.addresses, client) *> IO(isLoading.loadingAccounts.value = false)
              _ <- updateContracts(node.contracts, node.contractsABI, client) *> IO(isLoading.loadingContracts.value = false)
              _ <- updateReceipts(node.stxs, node.receipts, client) *> IO(isLoading.loadingReceipts.value = false)
              _ <- updateBlocks(node.blocks, client) *> IO(isLoading.loadingBlocks.value = false)
            } yield ()
        }
        .getOrElse(
          IO.unit
        )
    } else {
      IO.unit
    }

  def stream: Stream[IO, Unit] =
    Stream
      .awakeEvery[IO](5.seconds)
      .evalMap(_ => updateTask())
      .handleErrorWith { err =>
        Stream.eval(log.e(err.getMessage))
      }
}
