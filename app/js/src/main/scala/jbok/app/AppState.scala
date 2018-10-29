package jbok.app

import java.net.URI

import cats.implicits._
import com.thoughtworks.binding.Binding.{Var, Vars}
import jbok.app.api.BlockParam
import jbok.app.simulations.NodeInfo
import jbok.core.models._
import scodec.bits.ByteVector

case class ClientStatus(number: Var[BigInt] = Var(0),
                        gasPrice: Var[BigInt] = Var(0),
                        gasLimit: Var[BigInt] = Var(0),
                        miningStatus: Var[String] = Var("idle"))

case class BlockHistory(bestBlockNumber: Var[BigInt] = Var(-1), history: Vars[Block] = Vars.empty)

case class AppState(
    config: Var[AppConfig],
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
    contractAddress: Vars[Address] = Vars.empty[Address],
    contracts: Var[Map[String, Var[Map[Address, Var[Account]]]]] = Var(Map.empty),
    update: Var[Boolean] = Var(true)
) {
  def init() = {
    val p = for {
      sc    <- SimuClient(config.value.uri)
      nodes <- sc.simulation.getNodes
      _ = nodeInfos.value = nodes.map(node => node.id -> node).toMap
      simuAccounts <- sc.simulation.getAccounts()
      _ = simuAddress.value ++= simuAccounts.map(_._1)
      _ = nodes.foreach { node =>
        blocks.value += (node.id        -> BlockHistory())
        status.value += (node.id        -> ClientStatus())
        accounts.value += (node.id      -> Var(Map.empty[Address, Var[Account]]))
        contracts.value += (node.id     -> Var(Map.empty[Address, Var[Account]]))
        receipts.value += (node.id      -> Var(Map.empty[ByteVector, Var[Option[Receipt]]]))
        stxs.value += (node.id          -> Vars.empty[SignedTransaction])
        addressInNode.value += (node.id -> Vars.empty[Address])
      }
      jbokClients <- nodes.traverse(node => JbokClient(new URI(node.rpcAddr.toString)))
      _ = clients.value = nodes.map(_.id).zip(jbokClients).toMap
      nodeAddresses <- clients.value.toList.traverse(c => c._2.admin.listAccounts.map(c._1 -> _))
      _ = nodeAddresses.map {
        case (id, addresses) =>
          addressInNode.value(id).value ++= addresses
      }
    } yield ()

    p.unsafeToFuture()
  }

//  def initAccountsTx = {
//    _ = addressInNode.value.map {
//      case (id, addresses) =>
//        for {
//          stxs <- addresses.value.toList.traverse(address => clients.value(id).public.getAccountTransactions(address, 1, 10))
//          _ = addresses.zip(stxs)
//        } yield ()
//
//    }
//  }

  def updateStatus(id: String, client: JbokClient) = {
    val p = for {
      bestBlockNumber <- client.public.bestBlockNumber
      isMining        <- client.public.getMining
      gasPrice        <- client.public.getGasPrice
      miningStatus = if (isMining) "Mining" else "idle"
      _ = if (status.value.contains(id)) {
        status.value(id).number.value = bestBlockNumber
        status.value(id).miningStatus.value = miningStatus
        status.value(id).gasPrice.value = gasPrice
      } else {
        val cs = ClientStatus(Var(bestBlockNumber), Var(gasPrice), Var(0), Var(miningStatus))
        status.value += (id -> cs)
      }
    } yield ()
    p.unsafeToFuture()
  }

  def updateBlocks(id: String, client: JbokClient) = {
    val p = for {
      bestBlockNumber <- client.public.bestBlockNumber
      localBestBlockNumber = blocks.value.find(p => p._1 == id).map(_._2.bestBlockNumber.value.toInt).getOrElse(-1)
      xs <- (localBestBlockNumber.toInt until bestBlockNumber.toInt)
        .map(i => BigInt(i + 1))
        .toList
        .traverse(client.public.getBlockByNumber)
      _ = if (blocks.value.contains(id)) {
        blocks.value(id).bestBlockNumber.value = bestBlockNumber
        blocks.value(id).history.value ++= xs.flatten
      } else {
        val blockHistory = BlockHistory(bestBlockNumber = Var(bestBlockNumber))
        blockHistory.history.value ++= xs.flatten
        blocks.value += (id -> blockHistory)
      }
    } yield ()
    p.unsafeToFuture()
  }

  def updateAccounts(id: String, client: JbokClient) = {
    val addresses = simuAddress.value.toList ++ addressInNode.value
      .get(id)
      .map(_.value.toList)
      .getOrElse(List.empty[Address])
    val p = for {
      simuAccounts <- addresses.traverse(addr => client.public.getAccount(addr, BlockParam.Latest))
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
    p.unsafeToFuture()
  }

  def updateReceipts(id: String, client: JbokClient) = {
    val txHashes = stxs.value.get(id).map(_.value.toList.map(_.hash)).getOrElse(List.empty[ByteVector])
    val p = for {
      txReceipts <- txHashes.traverse(hash => client.public.getTransactionReceipt(hash))
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
    p.unsafeToFuture()
  }

  def updateContracts(id: String, client: JbokClient) = {
    val addresses = contractAddress.value.toList
    val p = for {
      simuAccounts <- addresses.traverse(addr => client.public.getAccount(addr, BlockParam.Latest))
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
    p.unsafeToFuture()
  }

  def updateTask() =
    nodeInfos.value.values
      .find(_.id == currentId.value.getOrElse(""))
      .map { nodeInfo =>
        clients.value.get(nodeInfo.id).map { client =>
          updateStatus(nodeInfo.id, client)
          updateBlocks(nodeInfo.id, client)
          updateAccounts(nodeInfo.id, client)
          updateContracts(nodeInfo.id, client)
          updateReceipts(nodeInfo.id, client)
        }
      }
}
