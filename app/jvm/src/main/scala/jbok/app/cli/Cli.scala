package jbok.app.cli

import java.time.{Instant, LocalDateTime, ZoneId}

import cats.effect._
import cats.implicits._
import fs2._
import jbok.core.api.JbokClient
import jbok.core.models.Address
import net.team2xh.onions.components._
import net.team2xh.onions.components.widgets.Label
import net.team2xh.scurses.Scurses

import scala.collection.mutable
import scala.concurrent.duration._

final class Cli[F[_]](client: JbokClient[F])(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  implicit val screen = new Scurses

  // layout
  val frame = Frame(Some("JBOK"), debug = true)
  val left  = frame.panel
  left.title = "status"
  val middle1 = left.splitRight
  val right1  = middle1.splitRight
  val right2  = right1.splitDown
  right1.title = "blocks"
  right2.title = "block"
  val middle2 = middle1.splitDown
  val middle3 = middle2.splitDown
  middle1.title = "incoming"
  middle2.title = "outgoing"
  middle3.title = "accounts"

  // history
  val bestBlockNumber     = Label(left, "0")
  val bestBlockHash       = Label(left, "")
  val pendingTransactions = Label(left, "0")
  val totalDifficulty     = Label(left, "0")
  def renderHistory =
    for {
      bestBlockOpt <- client.block.getBestBlockNumber >>= client.block.getBlockByNumber
      bestBlock    <- F.fromOption(bestBlockOpt, new Exception(""))
      td           <- client.block.getTotalDifficultyByHash(bestBlock.header.hash)
      txs          <- client.admin.pendingTransactions
    } yield {
      bestBlockNumber.text := s"bestBlockNumber: ${bestBlock.header.number}"
      bestBlockHash.text := s"bestBlockHash: ${bestBlock.header.hash.toHex}"
      totalDifficulty.text := s"totalDifficulty: ${td.getOrElse(BigInt(0))}"
      pendingTransactions.text := s"pendingTxs: ${txs.size}"
      ()
    }

  // peers
  val incoming = mutable.Map.empty[String, Label]
  val outgoing = mutable.Map.empty[String, Label]
  def renderPeers =
    for {
      incomingPeers <- client.admin.incomingPeers
      outgoingPeers <- client.admin.outgoingPeers
    } yield {
      incomingPeers.foreach { peer =>
        val text = s"incoming: ${peer.address}"
        if (incoming.contains(peer.uri.toString)) {
          incoming(peer.uri.toString).text := text
        } else {
          incoming += peer.uri.toString -> Label(middle1, text)
        }
      }

      outgoingPeers.foreach { peer =>
        val text = s"outgoing: ${peer.address}"
        if (outgoing.contains(peer.uri.toString)) {
          outgoing(peer.uri.toString).text := text
        } else {
          outgoing += peer.uri.toString -> Label(middle2, text)
        }
      }
    }

  // accounts
  val accountMap = mutable.Map.empty[Address, Label]
  def renderAccounts =
    for {
      addresses <- client.personal.listAccounts
      accounts  <- addresses.traverse(client.account.getAccount(_))
    } yield {
      addresses.zip(accounts).foreach {
        case (address, account) =>
          val text = s"${address}: balance=${account.balance} nonce=${account.nonce}"
          if (accountMap.contains(address)) {
            accountMap(address).text := text
          } else {
            accountMap += address -> Label(middle3, text)
          }
      }

    }

  // blocks
  val blocks = mutable.ArrayBuffer[Label](List.fill(10)(Label(right1, "")): _*)
  val renderBlocks = for {
    start   <- client.block.getBestBlockNumber
    headers <- client.block.getBlockHeadersByNumber((start - 10) max 0, 10)
    bodies  <- client.block.getBlockBodies(headers.map(_.hash))
  } yield {
    headers.zip(bodies).reverse.zipWithIndex.map {
      case ((header, body), idx) =>
        val datetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(header.unixTimestamp), ZoneId.systemDefault())
        val text     = s"(${header.number}) #${header.hash.toHex.take(7)} #txs=${body.transactionList.length} state=${header.stateRoot.toHex.take(7)} [${datetime}]"
        blocks(idx).text := text
    }
    ()
  }

  // block
  val blockNumber      = Label(right2, "")
  val blockHash        = Label(right2, "")
  val parentHash       = Label(right2, "")
  val beneficiary      = Label(right2, "")
  val stateRoot        = Label(right2, "")
  val transactionsRoot = Label(right2, "")
  val receiptsRoot     = Label(right2, "")
  val difficulty       = Label(right2, "")
  val gasLimit         = Label(right2, "")
  val gasUsed          = Label(right2, "")

  val renderBlock = for {
    number    <- client.block.getBestBlockNumber
    headerOpt <- client.block.getBlockHeaderByNumber(number)
  } yield {
    headerOpt match {
      case Some(header) =>
        blockNumber.text := s"number: ${header.number}"
        blockHash.text := s"hash: ${header.hash.toHex}"
        parentHash.text := s"parentHash: ${header.parentHash.toHex}"
        beneficiary.text := s"beneficiary: ${header.beneficiary.toHex}"
        stateRoot.text := s"stateRoot: ${header.stateRoot.toHex}"
        transactionsRoot.text := s"transactionsRoot: ${header.transactionsRoot.toHex}"
        receiptsRoot.text := s"receiptsRoot: ${header.receiptsRoot.toHex}"
        difficulty.text := s"difficulty: ${header.difficulty}"
        gasLimit.text := s"gasLimit: ${header.gasLimit}"
        gasUsed.text := s"gasUsed: ${header.gasUsed}"
        ()
      case None => ()
    }
  }

  // configs
  val bindAddr = Label(middle1, "")
  val serviceUri = Label(middle1, "")
  def renderConfigs = client.admin.getCoreConfig.map { config =>
    bindAddr.text := s"bindAddr: ${config.peer.bindAddr}"
    serviceUri.text := s"service: ${config.service.uri}"
    ()
  }

  // loop
  def loop(interval: FiniteDuration): Stream[F, Unit] =
    Stream
      .eval(F.delay(frame.show))
      .concurrently(
        Stream
          .eval(List(renderHistory, renderPeers, renderConfigs, renderBlocks, renderAccounts, renderBlock).sequence_)
          .evalTap(_ => F.delay(frame.redraw()))
          .repeat
          .metered(interval)
      )
      .onFinalize(F.delay(screen.close()))
}
