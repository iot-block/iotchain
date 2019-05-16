package jbok.app.cli

import cats.effect._
import cats.implicits._
import fs2._
import jbok.app.{AppModule, JbokClient}
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
  val right = left.splitRight

  // history
  val bestBlockNumber     = Label(left, "0")
  val bestBlockHash       = Label(left, "")
  val pendingTransactions = Label(left, "0")
  def renderHistory =
    for {
      bestBlockOpt <- client.public.bestBlockNumber >>= client.public.getBlockByNumber
      bestBlock <- F.fromOption(bestBlockOpt, new Exception(""))
      txs       <- client.admin.pendingTransactions
    } yield {
      bestBlockNumber.text := bestBlock.header.number.toString
      bestBlockHash.text := bestBlock.header.hash.toHex
      pendingTransactions.text := txs.size.toString
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
          val text = s"${peer.uri} [${peer.source}]"
          if (incoming.contains(peer.uri.toString)) {
            incoming(peer.uri.toString).text := text
          } else {
            incoming += peer.uri.toString -> Label(right, text)
          }
      }

      outgoingPeers.foreach { peer =>
          val text = s"${peer.uri} [${peer.source}]"
          if (outgoing.contains(peer.uri.toString)) {
            outgoing(peer.uri.toString).text := text
          } else {
            outgoing += peer.uri.toString -> Label(right, text)
          }
      }
    }

  // configs
  val peerService = Label(right, "")
  val syncService = Label(right, "")
  val rpcService  = Label(right, "")
  def renderConfigs = client.admin.getCoreConfig.map { config =>
    peerService.text := s"addr: ${config.peer.bindAddr}"
    syncService.text := s"addr: ${config.sync.syncAddr}"
    rpcService.text := s"enabled: ${config.rpc.enabled} addr: ${config.rpc.rpcAddr}"
    ()
  }

  // loop
  def loop(interval: FiniteDuration): Stream[F, Unit] =
    Stream.eval(F.delay(frame.show())) ++
      Stream
        .fixedDelay[F](interval)
        .evalMap { _ =>
          renderHistory >> renderPeers >> renderConfigs
        }
        .onFinalize(F.delay(screen.close()))
}

object Cli extends IOApp {
  override def run(args: List[String]): IO[ExitCode] =
    AppModule.locator[IO].use { objects =>
      val cli = new Cli[IO](objects.get[JbokClient[IO]])
      cli.loop(2.seconds).compile.drain.as(ExitCode.Success)
    }
}
