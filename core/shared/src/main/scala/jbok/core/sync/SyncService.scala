package jbok.core.sync

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2._
import jbok.core.History
import jbok.core.config.Configs.SyncConfig
import jbok.core.messages._

import scala.concurrent.ExecutionContext

case class SyncService[F[_]](config: SyncConfig, history: History[F])(implicit F: ConcurrentEffect[F],
                                                                      EC: ExecutionContext) {
  private[this] val log = org.log4s.getLogger

  val pipe: Pipe[F, Message, Message] = input => {
    val output = input.collect { case x: SyncMessage => x }.evalMap[F, Option[Message]] {
      case GetReceipts(hashes, id) =>
        for {
          receipts <- hashes.traverse(history.getReceiptsByHash).map(_.flatten)
        } yield Receipts(receipts, id).some

      case GetBlockBodies(hashes, id) =>
        for {
          bodies <- hashes.traverse(hash => history.getBlockBodyByHash(hash)).map(_.flatten)
        } yield BlockBodies(bodies, id).some

      case GetBlockHeaders(block, maxHeaders, skip, reverse, id) =>
        val blockNumber: F[Option[BigInt]] = block match {
          case Left(v)   => v.some.pure[F]
          case Right(bv) => history.getBlockHeaderByHash(bv).map(_.map(_.number))
        }

        blockNumber.flatMap {
          case Some(startBlockNumber) if startBlockNumber >= 0 && maxHeaders >= 0 && skip >= 0 =>
            val headersCount = math.min(maxHeaders, config.blockHeadersPerRequest)

            val range = if (reverse) {
              startBlockNumber to (startBlockNumber - (skip + 1) * headersCount + 1) by -(skip + 1)
            } else {
              startBlockNumber to (startBlockNumber + (skip + 1) * headersCount - 1) by (skip + 1)
            }

            for {
              headers <- range.toList.traverse(history.getBlockHeaderByNumber).map(_.flatten)
            } yield BlockHeaders(headers, id).some

          case _ =>
            log.warn(s"got request for block headers with invalid block hash/number")
            F.pure(None)
        }

      case _ => F.pure(None)
    }
    output.unNone
//=======
//import scala.concurrent.ExecutionContext
//
//case class SyncService[F[_]](
//                              peerManager: PeerManager[F],
//                              blockchain: History[F],
//                              stopWhenTrue: Signal[F, Boolean]
//)(implicit F: ConcurrentEffect[F], EC: ExecutionContext) {
//  private[this] val log = org.log4s.getLogger
//
//  def stream: Stream[F, Unit] =
//    peerManager
//      .subscribe()
//      .evalMap {
//        case PeerEvent.PeerRecv(peerId, message) =>
//          handleMessage(peerId, message).flatMap {
//            case Some((id, response)) => peerManager.sendMessage(id, response)
//            case None                 => F.unit
//          }
//        case _ => F.unit
//      }
//      .onFinalize(stopWhenTrue.set(true) *> F.delay(log.info(s"stop SyncService")))
//
//  def start: F[Unit] =
//    for {
//      _ <- stopWhenTrue.set(false)
//      _ <- F.start(stream.interruptWhen(stopWhenTrue).compile.drain).void
//      _ <- F.delay(log.info(s"start SyncService"))
//    } yield ()
//
//  def stop: F[Unit] = stopWhenTrue.set(true)
//
//  def handleMessage(peerId: PeerId, message: Message): F[Option[(PeerId, Message)]] = message match {
//    case GetReceipts(hashes) =>
//      for {
//        receipts <- hashes.traverse(blockchain.getReceiptsByHash).map(_.flatten)
//      } yield Some(peerId -> Receipts(receipts))
//
//    case GetBlockBodies(hashes) =>
//      for {
//        bodies <- hashes.traverse(hash => blockchain.getBlockBodyByHash(hash)).map(_.flatten)
//      } yield Some(peerId -> BlockBodies(bodies))
//
//    case request: GetBlockHeaders =>
//      val blockNumber: F[Option[BigInt]] = request.block match {
//        case Left(v)   => v.some.pure[F]
//        case Right(bv) => blockchain.getBlockHeaderByHash(bv).map(_.map(_.number))
//      }
//
//      blockNumber.flatMap {
//        case Some(startBlockNumber) if startBlockNumber >= 0 && request.maxHeaders >= 0 && request.skip >= 0 =>
//          val headersCount: BigInt = request.maxHeaders
//
//          val range = if (request.reverse) {
//            startBlockNumber to (startBlockNumber - (request.skip + 1) * headersCount + 1) by -(request.skip + 1)
//          } else {
//            startBlockNumber to (startBlockNumber + (request.skip + 1) * headersCount - 1) by (request.skip + 1)
//          }
//
//          for {
//            headers <- range.toList.traverse(blockchain.getBlockHeaderByNumber).map(_.flatten)
//          } yield Some(peerId -> BlockHeaders(headers))
//
//        case _ =>
//          log.warn(s"got request for block headers with invalid block hash/number: ${request}")
//          F.pure(none)
//      }
//
//    case _ => F.pure(none)
//>>>>>>> Stashed changes
  }
}
