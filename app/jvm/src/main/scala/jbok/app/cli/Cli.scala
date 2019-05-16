package jbok.app.cli

import java.nio.file.Paths
import java.time.{Instant, LocalDateTime, ZoneId}

import cats.effect._
import cats.implicits._
import fs2._
import jbok.app.AppModule
import jbok.app.config.FullConfig
import jbok.common.log.{Level, Logger}
import jbok.core.api.{JbokClient, JbokClientPlatform}
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
  val middle  = left.splitRight
  val right   = middle.splitRight
  val middle1 = middle.splitDown
  val middle2 = middle1.splitDown
  middle1.title = "incoming"
  middle2.title = "outgoing"

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
      frame.redraw()
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

      frame.redraw()
    }

  // blocks
  val blocks = mutable.ArrayBuffer[Label](List.fill(10)(Label(right, "")): _*)
  val renderBlocks = for {
    start   <- client.block.getBestBlockNumber
    headers <- client.block.getBlockHeadersByNumber((start - 10) max 0, 10)
    bodies  <- client.block.getBlockBodies(headers.map(_.hash))
  } yield {
    headers.zip(bodies).reverse.zipWithIndex.map { case ((header, body), idx) =>
      val datetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(header.unixTimestamp), ZoneId.systemDefault())
      val text = s"(${header.number}) #${header.hash.toHex.take(7)} #txs=${body.transactionList.length} state=${header.stateRoot.toHex.take(7)} [${datetime}]"
      blocks(idx).text := text
    }

    frame.redraw()
  }

  // configs
  val peerService = Label(middle1, "")
  def renderConfigs = client.admin.getCoreConfig.map { config =>
    peerService.text := s"local addr: ${config.peer.bindAddr}"
//    rpcService.text := s"enabled: ${config.rpc.enabled} addr: ${config.rpc.rpcAddr}"
    frame.redraw()
  }

  // loop
  def loop(interval: FiniteDuration): Stream[F, Unit] =
    Stream
      .eval(F.delay(frame.show))
      .concurrently(
        Stream
          .eval(renderHistory >> renderPeers >> renderConfigs >> renderBlocks >> T.sleep(interval))
          .repeat
      )
      .onFinalize(F.delay(screen.close()))
}

object Cli extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val resource = args match {
      case path :: _ => AppModule.resource[IO](Paths.get(path))
      case _         => AppModule.resource[IO]()
    }
    resource.use { objects =>
      val config = objects.get[FullConfig]
      JbokClientPlatform.resource[IO](s"https://${config.app.service.host}:${config.app.service.port}").use { client =>
        Logger.setRootLevel[IO](Level.Error).unsafeRunSync()
        val cli = new Cli[IO](client)
        cli.loop(5.seconds).compile.drain.as(ExitCode.Success)
      }
    }
  }
}
