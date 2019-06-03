package jbok.core.peer

import cats.data.Kleisli
import cats.effect.Sync
import cats.implicits._
import jbok.common.math.N
import jbok.core.messages.SignedTransactions
import jbok.core.models.Block

import scala.util.Random

object PeerSelector {
  type PeerSelector[F[_]] = Kleisli[F, List[Peer[F]], List[Peer[F]]]

  def apply[F[_]](run: List[Peer[F]] => F[List[Peer[F]]]): PeerSelector[F] =
    Kleisli(run)

  def all[F[_]: Sync]: PeerSelector[F] = PeerSelector(Sync[F].pure)

  def except[F[_]: Sync](peer: Peer[F]): PeerSelector[F] = PeerSelector(_.filterNot(_ == peer).pure[F])

  def one[F[_]: Sync](peer: Peer[F]): PeerSelector[F] = PeerSelector(_ => Sync[F].pure(List(peer)))

  def many[F[_]: Sync](list: List[Peer[F]]): PeerSelector[F] = PeerSelector(_ => Sync[F].pure(list))

  def withoutBlock[F[_]: Sync](block: Block): PeerSelector[F] = PeerSelector { peers =>
    peers
      .traverse[F, Option[Peer[F]]] { peer =>
        peer.hasBlock(block.header.hash).map {
          case true  => None
          case false => Some(peer)
        }
      }
      .map(_.flatten)
  }

  def withoutTxs[F[_]: Sync](stxs: SignedTransactions): PeerSelector[F] = PeerSelector { peers =>
    peers
      .traverse[F, Option[Peer[F]]] { peer =>
        peer.hasTxs(stxs).map {
          case true  => None
          case false => Some(peer)
        }
      }
      .map(_.flatten)
  }

  def bestPeer[F[_]: Sync](minTD: N): PeerSelector[F] = PeerSelector { peers =>
    peers
      .traverse(p => p.status.get.map(_.td).map(td => td -> p))
      .map(_.filter(_._1 > minTD).sortBy(-_._1).map(_._2))
  }

  def randomSelectSqrt[F[_]: Sync](min: Int): PeerSelector[F] = PeerSelector { peers =>
    val numberOfPeersToSend = math.max(math.sqrt(peers.size).toInt, min)
    Sync[F].pure(Random.shuffle(peers).take(numberOfPeersToSend))
  }
}
