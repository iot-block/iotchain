package jbok.core.pool

import cats.data.OptionT
import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.common._
import jbok.core.ledger.History
import jbok.core.models.{Block, BlockHeader}
import jbok.core.pool.BlockPool._
import scodec.bits.ByteVector

case class BlockPoolConfig(
    maxBlockAhead: Int = 10,
    maxBlockBehind: Int = 10
)

/**
  * [[BlockPool]] is responsible for stashing blocks with unknown ancestors
  * or candidate branches of blocks in consensus protocols with some finality criteria
  */
case class BlockPool[F[_]](
    history: History[F],
    config: BlockPoolConfig,
    blocks: Ref[F, Map[ByteVector, PooledBlock]], // blockHash -> block
    parentToChildren: Ref[F, Map[ByteVector, Set[ByteVector]]] // blockHash -> childrenHashes
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger("BlockPool")

  def contains(blockHash: ByteVector): F[Boolean] =
    blocks.get.map(_.contains(blockHash))

  def isDuplicate(blockHash: ByteVector): F[Boolean] =
    history.getBlockByHash(blockHash).map(_.isDefined) || contains(blockHash)

  def addBlock(block: Block): F[Option[Leaf]] =
    for {
      bestBlockNumber <- history.getBestBlockNumber
      _               <- cleanUp(bestBlockNumber)
      m               <- blocks.get
      leaf <- m.get(block.header.hash) match {
        case Some(_) =>
          log.debug(s"${block.tag} already pooled, ignore")
          F.pure(None)

        case None if isNumberOutOfRange(block.header.number, bestBlockNumber) =>
          log.debug(
            s"${block.tag} is outside accepted range [${bestBlockNumber} - ${config.maxBlockAhead}, ${bestBlockNumber} + ${config.maxBlockBehind}]")
          F.pure(None)

        case None =>
          for {
            parentTd <- history.getTotalDifficultyByHash(block.header.parentHash)
            l <- {
              parentTd match {
                case Some(_) =>
                  log.debug(s"${block.tag} will be on the main chain")
                  addBlock(block, parentTd) *> updateTotalDifficulties(block.header.hash)

                case None =>
                  val p: F[Option[Leaf]] = findClosestChainedAncestor(block).flatMap {
                    case Some(ancestor) =>
                      log.debug(s"${block.tag} to will be on a rooted side chain")
                      updateTotalDifficulties(ancestor)

                    case None =>
                      log.debug(s"${block.tag} with unknown relation to the main chain")
                      none[Leaf].pure[F]
                  }

                  addBlock(block, parentTd) *> p
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

  def getBlockByHash(blockHash: ByteVector): F[Option[Block]] =
    blocks.get.map(_.get(blockHash).map(_.block))

  def getHeader(hash: ByteVector): F[Option[BlockHeader]] =
    OptionT(history.getBlockHeaderByHash(hash))
      .orElseF(getBlockByHash(hash).map(_.map(_.header)))
      .value

  def getNBlocks(hash: ByteVector, n: Int): F[List[Block]] =
    for {
      pooledBlocks <- getBranch(hash, delete = false).map(_.take(n))
      result <- if (pooledBlocks.length == n) {
        pooledBlocks.pure[F]
      } else {
        val chainedBlockHash = pooledBlocks.headOption.map(_.header.parentHash).getOrElse(hash)
        history.getBlockByHash(chainedBlockHash).flatMap {
          case None =>
            F.pure(List.empty[Block])

          case Some(block) =>
            val remaining = n - pooledBlocks.length - 1
            val numbers   = (block.header.number - remaining) until block.header.number
            numbers.toList.map(history.getBlockByNumber).sequence.map(xs => (xs.flatten :+ block) ::: pooledBlocks)
        }
      }
    } yield result

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

  /**
    * Removes stale blocks - too old or too young in relation the current best block number
    * @param bestBlockNumber - best block number of the main chain
    */
  private def cleanUp(bestBlockNumber: BigInt): F[Unit] =
    for {
      m <- blocks.get
      staleHashes = m.values.toList.collect {
        case PooledBlock(b, _) if isNumberOutOfRange(b.header.number, bestBlockNumber) =>
          b.header.hash
      }
      _ <- if (staleHashes.nonEmpty) {
        log.debug(s"clean up ${staleHashes.length} pooled blocks")
        blocks.update(_ -- staleHashes) *> parentToChildren.update(_ -- staleHashes)
      } else {
        F.unit
      }
    } yield ()

  private def updateTotalDifficulties(ancestor: ByteVector): F[Option[Leaf]] = {
    val leaf = for {
      m        <- OptionT.liftF(blocks.get)
      qb       <- OptionT.fromOption[F](m.get(ancestor))
      td       <- OptionT.fromOption[F](qb.totalDifficulty)
      children <- OptionT.liftF(parentToChildren.get.map(_.getOrElse(ancestor, Set.empty)))

      leaf <- if (children.nonEmpty) {
        val updatedChildren =
          children.flatMap(m.get).map(qb => qb.copy(totalDifficulty = Some(td + qb.block.header.difficulty)))
        val l = for {
          _ <- blocks.update(_ ++ updatedChildren.map(x => x.block.header.hash -> x).toMap)
          l <- updatedChildren.toList
            .traverse(qb => updateTotalDifficulties(qb.block.header.hash))
            .map(_.flatten.maxBy(_.totalDifficulty))
        } yield l
        OptionT.liftF(l)
      } else {
        OptionT.some[F](Leaf(ancestor, td))
      }
    } yield leaf

    leaf.value
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
        case Some(PooledBlock(block, Some(_))) =>
          F.pure(Some(block.header.hash))

        case Some(PooledBlock(block, None)) =>
          go(blockMap, block)

        case None =>
          F.pure(None)
      }

    blocks.get.flatMap(blockMap => go(blockMap, block))
  }

  private def addBlock(block: Block, parentTd: Option[BigInt]): F[Unit] = {
    val td = parentTd.map(_ + block.header.difficulty)

    for {
      _        <- blocks.update(_ + (block.header.hash -> PooledBlock(block, td)))
      siblings <- parentToChildren.get.map(_.getOrElse(block.header.parentHash, Set.empty))
      _        <- parentToChildren.update(_ + (block.header.parentHash -> (siblings + block.header.hash)))
    } yield ()
  }

  private def isNumberOutOfRange(blockNumber: BigInt, bestBlockNumber: BigInt): Boolean =
    (blockNumber - bestBlockNumber > config.maxBlockAhead) ||
      (bestBlockNumber - blockNumber > config.maxBlockBehind)
}

object BlockPool {
  case class PooledBlock(block: Block, totalDifficulty: Option[BigInt])
  case class Leaf(hash: ByteVector, totalDifficulty: BigInt)

  def apply[F[_]: ConcurrentEffect](history: History[F],
                                    blockPoolConfig: BlockPoolConfig = BlockPoolConfig()): F[BlockPool[F]] =
    for {
      blocks           <- Ref.of[F, Map[ByteVector, PooledBlock]](Map.empty)
      parentToChildren <- Ref.of[F, Map[ByteVector, Set[ByteVector]]](Map.empty)
    } yield BlockPool(history, blockPoolConfig, blocks, parentToChildren)
}
