package jbok.core.ledger

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import fs2.async.Ref
import jbok.common._
import jbok.core.BlockChain
import jbok.core.ledger.BlockPool._
import jbok.core.models.Block
import scodec.bits.ByteVector

case class BlockPool[F[_]](
    blocks: Ref[F, Map[ByteVector, QueuedBlock]],
    parentToChildren: Ref[F, Map[ByteVector, Set[ByteVector]]],
    blockChain: BlockChain[F],
    maxQueuedBlockNumberAhead: Int,
    maxQueuedBlockNumberBehind: Int
)(implicit F: Sync[F]) {
  private[this] val log = org.log4s.getLogger

  def contains(blockHash: ByteVector): F[Boolean] =
    blocks.get.map(_.contains(blockHash))

  def addBlock(block: Block, bestBlockNumber: BigInt): F[Option[Leaf]] = {
    import block.header._

    for {
      _ <- cleanUp(bestBlockNumber)
      m <- blocks.get
      leaf <- m.get(hash) match {
        case Some(_) =>
          log.info(s"Block(${hash}) already in, ignore")
          F.pure(None)

        case None if isNumberOutOfRange(number, bestBlockNumber) =>
          log.info(s"Block(${hash} is outside accepted range. Current best block number is: $bestBlockNumber")
          F.pure(None)

        case None =>
          for {
            parentTd <- blockChain.getTotalDifficultyByHash(parentHash)
            l <- {
              parentTd match {
                case Some(_) =>
                  log.info(s"add a new block(${hash}) with parent on the main chain")
                  addBlock(block, parentTd) *> updateTotalDifficulties(hash)

                case None =>
                  val p: F[Option[Leaf]] = findClosestChainedAncestor(block).flatMap {
                    case Some(ancestor) =>
                      log.info(s"add a new block (${hash}) to a rooted sidechain")
                      updateTotalDifficulties(ancestor)

                    case None =>
                      log.info(s"add a new block (${hash}) with unknown relation to the main chain")
                      none[Leaf].pure[F]
                  }

                  addBlock(block, parentTd) *> p
              }
            }
          }  yield l
      }
    } yield leaf
  }

  def getBranch(descendant: ByteVector, dequeue: Boolean): F[List[Block]] = {
    def getBranch0(hash: ByteVector, childShared: Boolean): F[List[Block]] = {
      for {
        m <- blocks.get
        blocks <- m.get(hash) match {
          case Some(QueuedBlock(block, _)) =>
            import block.header.parentHash

            for {
              isShared <- childShared.pure[F] || parentToChildren.get.map(_.get(hash).exists(_.nonEmpty))
              _ <- if (!isShared && dequeue) {
                for {
                  siblings <- parentToChildren.get.map(_.get(parentHash))
                  _ <- siblings match {
                    case Some(sbls) => parentToChildren.modify(_ + (parentHash -> (sbls - hash)))
                    case None => F.unit
                  }
                  _ <- blocks.modify(_ - hash)
                } yield ()
              } else {
                F.unit
              }
              blocks <- getBranch0(parentHash, isShared)
            } yield block :: blocks

          case None =>
            F.pure(Nil)
        }
      } yield blocks
    }

    getBranch0(descendant, childShared = false).map(_.reverse)
  }

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
  private def cleanUp(bestBlockNumber: BigInt): F[Unit] = {
    for {
      m <- blocks.get
      staleHashes = m.values.toList.collect {
        case QueuedBlock(b, _) if isNumberOutOfRange(b.header.number, bestBlockNumber) =>
          b.header.hash
      }
      _ <- if (staleHashes.nonEmpty) {
        log.info(s"clean up ${staleHashes.length} staleHashes")
        blocks.modify(_ -- staleHashes) *> parentToChildren.modify(_ -- staleHashes)
      } else {
        F.unit
      }
    } yield ()
  }

  private def updateTotalDifficulties(ancestor: ByteVector): F[Option[Leaf]] = {
    val leaf = for {
      m <- OptionT.liftF(blocks.get)
      qb <- OptionT.fromOption[F](m.get(ancestor))
      td <- OptionT.fromOption[F](qb.totalDifficulty)
      children <- OptionT.liftF(parentToChildren.get.map(_.getOrElse(ancestor, Set.empty)))

      leaf <- if (children.nonEmpty) {
        val updatedChildren = children.flatMap(m.get).map(qb => qb.copy(totalDifficulty = Some(td + qb.block.header.difficulty)))
        val l = for {
          _ <- blocks.modify(_ ++ updatedChildren.map(x => x.block.header.hash -> x).toMap)
          l <- updatedChildren.toList.traverse(qb => updateTotalDifficulties(qb.block.header.hash)).map(_.flatten.maxBy(_.totalDifficulty))
        } yield l
        OptionT.liftF(l)
      } else {
        OptionT.some[F](Leaf(ancestor, td))
      }
    } yield leaf

    leaf.value
  }

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

  def apply[F[_]: Sync](blockChain: BlockChain[F], maxQueuedBlockNumberAhead: Int, maxQueuedBlockNumberBehind: Int): F[BlockPool[F]] = for {
    blocks <- fs2.async.refOf[F, Map[ByteVector, QueuedBlock]](Map.empty)
    parentToChildren <- fs2.async.refOf[F, Map[ByteVector, Set[ByteVector]]](Map.empty)
  } yield BlockPool(blocks, parentToChildren, blockChain, maxQueuedBlockNumberAhead, maxQueuedBlockNumberBehind)
}
