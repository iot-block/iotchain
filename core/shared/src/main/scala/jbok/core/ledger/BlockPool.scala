package jbok.core.ledger

import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import jbok.core.ledger.BlockPool._
import jbok.core.models.Block
import scodec.bits.ByteVector

case class BlockPool[F[_]](
    blocks: Ref[F, Map[ByteVector, QueuedBlock]],
    parentToChildren: Ref[F, Map[ByteVector, Set[ByteVector]]],
    maxQueuedBlockNumberAhead: Int,
    maxQueuedBlockNumberBehind: Int
)(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  def contains(blockHash: ByteVector): F[Boolean] =
    blocks.get.map(_.contains(blockHash))

  def addBlock(block: Block): F[Option[Leaf]] = ???

  def getBranch(descendant: ByteVector, dequeue: Boolean): F[List[Block]] = ???

  def getBlockByHash(blockHash: ByteVector): F[Option[Block]] =
    blocks.get.map(_.get(blockHash).map(_.block))

  /**
    * Removes a whole subtree begining with the ancestor. To be used when ancestor fails to execute
    *
    * @param ancestor hash of the ancestor block
    */
  def removeSubtree(ancestor: ByteVector): F[Unit] =
    for {
      m <- blocks.get
      _ <- m.get(ancestor) match {
        case Some(QueuedBlock(block, _)) =>
          for {
            children <- parentToChildren.get.map(_.getOrElse(ancestor, Set.empty))
            _ <- children.toList.traverse(x => removeSubtree(x))
            _ <- blocks.modify(_ - block.header.hash)
            _ <- parentToChildren.modify(_ - block.header.hash)
          } yield ()
        case _ => F.unit
      }
    } yield ()

  /**
    * Removes stale blocks - too old or too young in relation the current best block number
    * @param bestBlockNumber - best block number of the main chain
    */
  private def cleanUp(bestBlockNumber: BigInt): Unit = ???

  private def updateTotalDifficulties(ancestor: ByteVector): Option[Leaf] = ???

  /**
    * Find a closest (youngest) chained ancestor. Chained means being part of a known chain, thus having total
    * difficulty defined
    *
    * @param descendant the block we start the search from
    * @return hash of the ancestor, if found
    */
  private def findClosestChainedAncestor(descendant: Block): F[Option[ByteVector]] =
    blocks.get.flatMap(_.get(descendant.header.parentHash) match {
      case Some(QueuedBlock(block, Some(_))) =>
        F.pure(Some(block.header.hash))

      case Some(QueuedBlock(block, None)) =>
        findClosestChainedAncestor(block)

      case None =>
        F.pure(None)
    })

  private def addBlock(block: Block, parentTd: Option[BigInt]): F[Unit] = {
    import block.header._
    val td = parentTd.map(_ + difficulty)

    for {
      _ <- blocks.modify(_ + (hash -> QueuedBlock(block, td)))
      siblings <- parentToChildren.get.map(_.getOrElse(parentHash, Set.empty))
      _ <- parentToChildren.modify(_ + (parentHash -> (siblings + hash)))
    } yield ()
  }

  private def isNumberOutOfRange(blockNumber: BigInt, bestBlockNumber: BigInt): Boolean =
    blockNumber - bestBlockNumber > maxQueuedBlockNumberAhead ||
      bestBlockNumber - blockNumber > maxQueuedBlockNumberBehind
}

object BlockPool {
  case class QueuedBlock(block: Block, totalDifficulty: Option[BigInt])
  case class Leaf(hash: ByteVector, totalDifficulty: BigInt)

  def apply[F[_]: Sync]: F[BlockPool[F]] = for {
    blocks <- fs2.async.refOf[F, Map[ByteVector, QueuedBlock]](Map.empty)
    parentToChildren <- fs2.async.refOf[F, Map[ByteVector, Set[ByteVector]]](Map.empty)
    maxQueuedBlockNumberAhead = 1
    maxQueuedBlockNumberBehind = 2
  } yield BlockPool(blocks, parentToChildren, maxQueuedBlockNumberAhead, maxQueuedBlockNumberBehind)
}
