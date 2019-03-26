package jbok.app

import java.net.URI

import cats.effect.IO
import cats.implicits._
import com.thoughtworks.binding.Binding.{Var, Vars}
import fs2.Stream
import jbok.app.api.NodeInfo
import jbok.app.client.JbokClient
import jbok.common.execution._
import jbok.core.models._
import jbok.sdk.api.BlockParam
import jbok.evm.solidity.ABIDescription.FunctionDescription
import org.scalajs.dom.Event
import scodec.bits.ByteVector

import scala.concurrent.duration._

final case class ClientStatus(number: Var[BigInt] = Var(0),
                              gasPrice: Var[BigInt] = Var(0),
                              gasLimit: Var[BigInt] = Var(0),
                              miningStatus: Var[String] = Var("idle"))

final case class BlockHistory(bestBlockNumber: Var[BigInt] = Var(-1), history: Vars[Block] = Vars.empty)

final case class Contract(address: Address, abi: List[FunctionDescription])

final case class AppState(
    config: Var[AppConfig],
    hrefHandler: Event => Unit,
    currentId: Var[Option[String]] = Var(None),
    clients: Var[Map[String, JbokClient]] = Var(Map.empty),
    status: Var[Map[String, ClientStatus]] = Var(Map.empty),
    nodeInfos: Var[Map[String, NodeInfo]] = Var(Map.empty),
    blocks: Var[Map[String, BlockHistory]] = Var(Map.empty),
    accounts: Var[Map[String, Var[Map[Address, Var[Account]]]]] = Var(Map.empty),
    stxs: Var[Map[String, Vars[SignedTransaction]]] = Var(Map.empty),
    receipts: Var[Map[String, Var[Map[ByteVector, Var[Option[Receipt]]]]]] = Var(Map.empty),
    simuAddress: Vars[Address] = Vars.empty[Address],
    addressInNode: Var[Map[String, Vars[Address]]] = Var(Map.empty),
    contractInfo: Vars[Contract] = Vars.empty[Contract],
    contracts: Var[Map[String, Var[Map[Address, Var[Account]]]]] = Var(Map.empty),
    selectedAccount: Var[Option[(Address, Account, List[SignedTransaction])]] = Var(None),
    selectedBlock: Var[Option[Block]] = Var(None),
    update: Var[Boolean] = Var(true)
) {
  def init() = {
    val p = for {
      sc           <- SimuClient(config.value.uri)
      nodes        <- sc.simulation.getNodes
      simuAccounts <- sc.simulation.getAccounts
      _ = simuAddress.value ++= simuAccounts.map(_._1)
      _ = nodes.foreach(addNodeInfo)
      jbokClients <- nodes.traverse[IO, JbokClient](node => jbok.app.client.JbokClient(new URI(node.rpcAddr.toString)))
      _ = clients.value = nodes.map(_.id).zip(jbokClients).toMap
    } yield ()

    p.unsafeToFuture()
    stream.compile.drain.unsafeToFuture()
  }

  def addNodeInfo(node: NodeInfo): Unit = {
    nodeInfos.value += (node.id)    -> node
    blocks.value += (node.id        -> BlockHistory())
    status.value += (node.id        -> ClientStatus())
    accounts.value += (node.id      -> Var(Map.empty[Address, Var[Account]]))
    contracts.value += (node.id     -> Var(Map.empty[Address, Var[Account]]))
    receipts.value += (node.id      -> Var(Map.empty[ByteVector, Var[Option[Receipt]]]))
    stxs.value += (node.id          -> Vars.empty[SignedTransaction])
    addressInNode.value += (node.id -> Vars.empty[Address])
  }

  def removeNodeInfo(node: NodeInfo): Unit = {
    nodeInfos.value -= node.id
    blocks.value -= node.id
    status.value -= node.id
    accounts.value -= node.id
    contracts.value -= node.id
    receipts.value -= node.id
    stxs.value -= node.id
    addressInNode.value -= node.id
    clients.value -= node.id
  }

  def updateStatus(id: String, client: JbokClient): IO[Unit] =
    for {
      bestBlockNumber <- client.public.bestBlockNumber
      block           <- client.public.getBlockByNumber(bestBlockNumber)
      isMining        <- client.public.isMining
      gasPrice        <- client.public.getGasPrice
      miningStatus = if (isMining) "Mining" else "idle"
      gasLimit     = block.map(_.header.gasLimit).getOrElse(BigInt(0))
      _ = if (status.value.contains(id)) {
        status.value(id).number.value = bestBlockNumber
        status.value(id).miningStatus.value = miningStatus
        status.value(id).gasPrice.value = gasPrice
        status.value(id).gasLimit.value = gasLimit
      } else {
        val cs = ClientStatus(Var(bestBlockNumber), Var(gasPrice), Var(0), Var(miningStatus))
        status.value += (id -> cs)
      }
    } yield ()

  def updateBlocks(id: String, client: JbokClient): IO[Unit] =
    for {
      bestBlockNumber <- client.public.bestBlockNumber
      localBestBlockNumber = blocks.value.find(p => p._1 == id).map(_._2.bestBlockNumber.value.toInt).getOrElse(-1)
      expire               = bestBlockNumber - 100
      start                = (localBestBlockNumber + 1).max(expire.toInt)
      xs <- (start.toInt to bestBlockNumber.toInt)
        .map(BigInt(_))
        .toList
        .traverse(client.public.getBlockByNumber)
      _ = if (blocks.value.contains(id)) {
        blocks.value(id).bestBlockNumber.value = bestBlockNumber
        blocks.value(id).history.value --= blocks.value(id).history.value.filter(_.header.number < expire)
        blocks.value(id).history.value ++= xs.flatten
      } else {
        val blockHistory = BlockHistory(bestBlockNumber = Var(bestBlockNumber))
        blockHistory.history.value ++= xs.flatten
        blocks.value += (id -> blockHistory)
      }
    } yield ()

  def updateAddresses(id: String, client: JbokClient): IO[Unit] =
    for {
      addresses <- client.personal.listAccounts
      _ = if (addressInNode.value.contains(id)) {
        addressInNode.value(id).value ++= addresses
        addressInNode.value(id).value.distinct
      } else {
        addressInNode.value += id -> Vars(addresses: _*)
      }
    } yield ()

  def updateAccounts(id: String, client: JbokClient): IO[Unit] = {
    val addresses = addressInNode.value
      .get(id)
      .map(_.value.toList)
      .getOrElse(List.empty[Address])
    for {
      simuAccounts <- addresses.traverse[IO, Account](addr => client.public.getAccount(addr, BlockParam.Latest))
      _ = if (accounts.value.contains(id)) {
        val mAccount = accounts.value(id)
        addresses.zip(simuAccounts).foreach {
          case (address, account) =>
            if (mAccount.value.contains(address)) {
              mAccount.value(address).value = account
            } else {
              mAccount.value += (address -> Var(account))
            }
        }
      } else {
        val mAccount = addresses
          .zip(simuAccounts)
          .map {
            case (address, account) =>
              address -> Var(account)
          }
          .toMap
        accounts.value += (id -> Var(mAccount))
      }
    } yield ()
  }

  def updateReceipts(id: String, client: JbokClient): IO[Unit] = {
    val txHashes = stxs.value.get(id).map(_.value.toList.map(_.hash)).getOrElse(List.empty[ByteVector])
    for {
      txReceipts <- txHashes.traverse[IO, Option[Receipt]](hash => client.public.getTransactionReceipt(hash))
      _ = if (receipts.value.contains(id)) {
        txHashes.zip(txReceipts).foreach {
          case (hash, receipt) => {
            if (receipts.value(id).value.contains(hash))
              receipts.value(id).value.get(hash).foreach(_.value = receipt)
            else
              receipts.value(id).value += (hash -> Var(receipt))
          }
        }
      } else {
        val receipt = txHashes
          .zip(txReceipts)
          .map {
            case (hash, receipt) => {
              hash -> Var(receipt)
            }
          }
          .toMap
        receipts.value += (id -> Var(receipt))
      }
    } yield ()
  }

  def updateContracts(id: String, client: JbokClient): IO[Unit] = {
    val addresses = contractInfo.value.toList.map(_.address)
    for {
      simuAccounts <- addresses.traverse[IO, Account](addr => client.public.getAccount(addr, BlockParam.Latest))
      _ = if (contracts.value.contains(id)) {
        val mContract = contracts.value(id)
        addresses.zip(simuAccounts).foreach {
          case (address, account) =>
            if (mContract.value.contains(address)) {
              mContract.value(address).value = account
            } else {
              mContract.value += (address -> Var(account))
            }
        }
      } else {
        val mContract = addresses
          .zip(simuAccounts)
          .map {
            case (address, account) =>
              address -> Var(account)
          }
          .toMap
        contracts.value += (id -> Var(mContract))
      }
    } yield ()
  }

  def updateTask(): IO[Unit] =
    if (update.value) {
      (for {
        currId   <- currentId.value
        nodeInfo <- nodeInfos.value.get(currId)
        client   <- clients.value.get(nodeInfo.id)
      } yield nodeInfo -> client)
        .map {
          case (nodeInfo, client) =>
            for {
              _ <- updateStatus(nodeInfo.id, client)
              _ <- updateBlocks(nodeInfo.id, client)
              _ <- updateAddresses(nodeInfo.id, client)
              _ <- updateAccounts(nodeInfo.id, client)
              _ <- updateContracts(nodeInfo.id, client)
              _ <- updateReceipts(nodeInfo.id, client)
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
        Stream.eval(IO.delay(println(err)))
      }

}
