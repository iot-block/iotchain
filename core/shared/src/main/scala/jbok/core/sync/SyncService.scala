package jbok.core.sync

import cats.effect.Sync
import cats.implicits._
import jbok.core.Blockchain
import jbok.core.messages._
import fs2._

class SyncService[F[_]](blockchain: Blockchain[F])(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  def handleMessages(messages: Stream[F, MessageFromPeer]): Stream[F, MessageToPeer] =
    messages
      .evalMap(from =>
        handleMessage(from).map {
          case Some(msg) => MessageToPeer(msg, from.peerId).some
          case None => None
      })
      .unNone

  def handleMessage(message: MessageFromPeer): F[Option[Message]] = message match {
    case MessageFromPeer(GetReceipts(hashes), _) =>
      for {
        receipts <- hashes.traverse(blockchain.getReceiptsByHash).map(_.flatten)
      } yield Receipts(receipts).some

    case MessageFromPeer(GetBlockBodies(hashes), _) =>
      for {
        bodies <- hashes.traverse(hash => blockchain.getBlockBodyByHash(hash)).map(_.flatten)
      } yield BlockBodies(bodies).some

    case MessageFromPeer(request: GetBlockHeaders, _) =>
      val blockNumber: F[Option[BigInt]] = request.block match {
        case Left(v) => v.some.pure[F]
        case Right(bv) => blockchain.getBlockHeaderByHash(bv).map(_.map(_.number))
      }

      blockNumber.flatMap {
        case Some(startBlockNumber) if startBlockNumber >= 0 && request.maxHeaders >= 0 && request.skip >= 0 =>
          val headersCount: BigInt = request.maxHeaders

          val range = if (request.reverse) {
            startBlockNumber to (startBlockNumber - (request.skip + 1) * headersCount + 1) by -(request.skip + 1)
          } else {
            startBlockNumber to (startBlockNumber + (request.skip + 1) * headersCount - 1) by (request.skip + 1)
          }

          for {
            headers <- range.toList.traverse(blockchain.getBlockHeaderByNumber).map(_.flatten)
          } yield BlockHeaders(headers).some

        case _ =>
          log.warn(s"got request for block headers with invalid block hash/number: ${request}")
          F.pure(none)
      }

    case _ => F.pure(none)
  }
}

object SyncService {
  def apply[F[_]](blockchain: Blockchain[F])(implicit F: Sync[F]): SyncService[F] = new SyncService(blockchain)
}
