package jbok.core.pool

import cats.data.OptionT
import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.common._
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.common.math.implicits._
import jbok.core.config.BlockPoolConfig
import jbok.core.ledger.History
import jbok.core.models.Block
import jbok.core.pool.BlockPool._
import scodec.bits.ByteVector

/**
  * `BlockPool` is responsible for stashing blocks with unknown ancestors
  * or candidate branches of blocks in consensus protocols with some finality criteria
  */
final class BlockPool[F[_]](
    config: BlockPoolConfig,
    history: History[F]
)(implicit F: ConcurrentEffect[F]) {
  // blockHash -> block
  private val blocks: Ref[F, Map[ByteVector, PooledBlock]] = Ref.unsafe[F, Map[ByteVector, PooledBlock]](Map.empty)

  // blockHash -> childrenHashes
  private val parentToChildren: Ref[F, Map[ByteVector, Set[ByteVector]]] = Ref.unsafe[F, Map[ByteVector, Set[ByteVector]]](Map.empty)

  private[this] val log = Logger[F]

  def contains(blockHash: ByteVector): F[Boolean] =
    blocks.get.map(_.contains(blockHash))

  def getPooledBlockByHash(hash: ByteVector): F[Option[PooledBlock]] =
    blocks.get.map(_.get(hash))

  def getBlockFromPoolOrHistory(hash: ByteVector): F[Option[Block]] =
    OptionT(getPooledBlockByHash(hash).map(_.map(_.block)))
      .orElseF(history.getBlockByHash(hash))
      .value

  def addBlock(block: Block): F[Option[Leaf]] =
    for {
      bestBlockNumber <- history.getBestBlockNumber
      _               <- cleanStaleBlocks(bestBlockNumber)
      m               <- blocks.get
      leaf <- m.get(block.header.hash) match {
        case Some(_) =>
          log.i(s"${block.tag} already pooled, ignore").as(None)

        case None if isNumberOutOfRange(block.header.number, bestBlockNumber) =>
          log.i(s"${block.tag} is outside accepted range [${bestBlockNumber} - ${config.maxBlockAhead}, ${bestBlockNumber} + ${config.maxBlockBehind}]").as(None)

        case None =>
          for {
            parentTd <- history.getTotalDifficultyByHash(block.header.parentHash)
            l <- {
              parentTd match {
                case Some(_) =>
                  log.i(s"${block.tag} will be on the main chain") >>
                  addBlock(block, parentTd) >> updateTotalDifficulties(block.header.hash)

                case None =>
                  val p: F[Option[Leaf]] = findClosestChainedAncestor(block).flatMap {
                    case Some(ancestor) =>
                      log.i(s"${block.tag} will be on a rooted side chain") >>
                      updateTotalDifficulties(ancestor)

                    case None =>
                      log.i(s"${block.tag} with unknown relation to the main chain").as(None)
                  }

                  addBlock(block, parentTd) >> p
              }
            }
          } yield l
      }
    } yield leaf

  /**
    * get a branch from the newest descendant block upwards to its oldest ancestor
    * @param blockHash the newest block hash
    * @param delete should the branch be removed. shared block(with other children) won't be removed
    * @return the full branch from oldest ancestor to descendant, no matter `delete`
    */
  def getBranch(blockHash: ByteVector, delete: Boolean): F[List[Block]] = {
    def go(blockMap: Map[ByteVector, PooledBlock], hash: ByteVector, childShared: Boolean): F[List[Block]] =
      blockMap.get(hash) match {
        case Some(PooledBlock(block, _)) =>
          for {
            isShared <- childShared.pure[F] || parentToChildren.get.map(_.get(hash).exists(_.nonEmpty))
            _ <- if (!isShared && delete) {
              for {
                siblingsOpt <- parentToChildren.get.map(_.get(block.header.parentHash))
                _ <- siblingsOpt match {
                  case Some(siblings) => parentToChildren.update(_ + (block.header.parentHash -> (siblings - hash)))
                  case None           => F.unit
                }
                _ <- blocks.update(_ - hash)
              } yield ()
            } else {
              F.unit
            }
            blocks <- go(blockMap, block.header.parentHash, isShared)
          } yield block :: blocks

        case None =>
          F.pure(Nil)
      }

    blocks.get.flatMap(blockMap => go(blockMap, blockHash, childShared = false).map(_.reverse))
  }

  /** Removes a whole subtree starts with the ancestor. To be used when ancestor fails to execute */
  def removeSubtree(ancestor: ByteVector): F[Unit] =
    for {
      m <- blocks.get
      _ <- m.get(ancestor) match {
        case Some(PooledBlock(block, _)) =>
          for {
            children <- parentToChildren.get.map(_.getOrElse(ancestor, Set.empty))
            _        <- children.toList.traverse(x => removeSubtree(x))
            _        <- blocks.update(_ - block.header.hash)
            _        <- parentToChildren.update(_ - block.header.hash)
          } yield ()
        case _ => F.unit
      }
    } yield ()

  /** Removes stale blocks - too old or too young in relation the current best block number */
  private def cleanStaleBlocks(bestBlockNumber: N): F[Unit] =
    for {
      m <- blocks.get
      staleHashes = m.values.toList.collect {
        case PooledBlock(b, _) if isNumberOutOfRange(b.header.number, bestBlockNumber) =>
          b.header.hash
      }
      _ <- if (staleHashes.nonEmpty) {
        log.debug(s"clean up ${staleHashes.length} pooled blocks") >>
        blocks.update(_ -- staleHashes) >> parentToChildren.update(_ -- staleHashes)
      } else {
        F.unit
      }
    } yield ()

  /** Recursively update total difficulties for the ancestor's descendants (iff ancestor's td is defined) */
  private def updateTotalDifficulties(ancestor: ByteVector): F[Option[Leaf]] = {
    val p = for {
      blockMap <- OptionT.liftF(blocks.get)
      pooled   <- OptionT.fromOption[F](blockMap.get(ancestor))
      td       <- OptionT.fromOption[F](pooled.totalDifficulty)
      children <- OptionT.liftF(parentToChildren.get.map(_.getOrElse(ancestor, Set.empty)))

      leaf <- if (children.nonEmpty) {
        val updatedChildren =
          children.flatMap(blockMap.get).map(pb => pb.copy(totalDifficulty = Some(td + pb.block.header.difficulty)))
        val l = for {
          _ <- blocks.update(_ ++ updatedChildren.map(x => x.block.header.hash -> x).toMap) // update children td
          l <- updatedChildren.toList
            .traverse(pb => updateTotalDifficulties(pb.block.header.hash)) // recursively update children's children
            .map(_.flatten.maxBy(_.totalDifficulty))
        } yield l
        OptionT.liftF(l)
      } else {
        OptionT.some[F](Leaf(ancestor, td)) // ancestor has no children, so it is a leaf
      }
    } yield leaf
    p.value
  }

  /**
    * Find a closest (youngest) chained ancestor.
    * Chained means being part of a known chain, thus having total difficulty defined
    *
    * @param block the block we start the search from
    * @return hash of the ancestor, if found
    */
  private def findClosestChainedAncestor(block: Block): F[Option[ByteVector]] = {
    def go(blockMap: Map[ByteVector, PooledBlock], block: Block): F[Option[ByteVector]] =
      blockMap.get(block.header.parentHash) match {
        case Some(PooledBlock(parentBlock, Some(_))) =>
          F.pure(Some(parentBlock.header.hash))

        case Some(PooledBlock(parentBlock, None)) =>
          go(blockMap, parentBlock)

        case None =>
          F.pure(None)
      }

    blocks.get.flatMap(blockMap => go(blockMap, block))
  }

  private def addBlock(block: Block, parentTd: Option[N]): F[Unit] = {
    val td = parentTd.map(_ + block.header.difficulty)
    for {
      _        <- blocks.update(_ + (block.header.hash -> PooledBlock(block, td)))
      siblings <- parentToChildren.get.map(_.getOrElse(block.header.parentHash, Set.empty))
      _        <- parentToChildren.update(_ + (block.header.parentHash -> (siblings + block.header.hash)))
    } yield ()
  }

  private def isNumberOutOfRange(blockNumber: N, bestBlockNumber: N): Boolean =
    (blockNumber - bestBlockNumber > config.maxBlockAhead) ||
      (bestBlockNumber - blockNumber > config.maxBlockBehind)
}

object BlockPool {
  final case class PooledBlock(block: Block, totalDifficulty: Option[N])
  final case class Leaf(hash: ByteVector, totalDifficulty: N)
}
