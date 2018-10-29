package jbok.core.pool

import cats.data.OptionT
import cats.effect.ConcurrentEffect
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.common._
import jbok.core.History
import jbok.core.models.{Block, BlockHeader}
import jbok.core.pool.BlockPool._
import scodec.bits.ByteVector

case class BlockPoolConfig(
    maxBlockAhead: Int = 10,
    maxBlockBehind: Int = 10
)

case class BlockPool[F[_]](
    history: History[F],
    blockPoolConfig: BlockPoolConfig,
    blocks: Ref[F, Map[ByteVector, QueuedBlock]],
    parentToChildren: Ref[F, Map[ByteVector, Set[ByteVector]]],
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = org.log4s.getLogger

  def contains(blockHash: ByteVector): F[Boolean] =
    blocks.get.map(_.contains(blockHash))

  def isDuplicate(blockHash: ByteVector): F[Boolean] =
    history.getBlockByHash(blockHash).map(_.isDefined) || contains(blockHash)

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
            parentTd <- history.getTotalDifficultyByHash(parentHash)
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
          } yield l
      }
    } yield leaf
  }

  def getBranch(descendant: ByteVector, dequeue: Boolean): F[List[Block]] = {
    def getBranch0(hash: ByteVector, childShared: Boolean): F[List[Block]] =
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
                    case Some(sbls) => parentToChildren.update(_ + (parentHash -> (sbls - hash)))
                    case None       => F.unit
                  }
                  _ <- blocks.update(_ - hash)
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

    getBranch0(descendant, childShared = false).map(_.reverse)
  }

  def getBlockByHash(blockHash: ByteVector): F[Option[Block]] =
    blocks.get.map(_.get(blockHash).map(_.block))

  def getHeader(hash: ByteVector): F[Option[BlockHeader]] =
    OptionT(history.getBlockHeaderByHash(hash))
      .orElseF(getBlockByHash(hash).map(_.map(_.header)))
      .value

  def getNBlocks(hash: ByteVector, n: Int): F[List[Block]] =
    for {
      pooledBlocks <- getBranch(hash, dequeue = false).map(_.take(n))
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
        case QueuedBlock(b, _) if isNumberOutOfRange(b.header.number, bestBlockNumber) =>
          b.header.hash
      }
      _ <- if (staleHashes.nonEmpty) {
        log.info(s"clean up ${staleHashes.length} staleHashes")
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
      _        <- blocks.update(_ + (hash -> QueuedBlock(block, td)))
      siblings <- parentToChildren.get.map(_.getOrElse(parentHash, Set.empty))
      _        <- parentToChildren.update(_ + (parentHash -> (siblings + hash)))
    } yield ()
  }

  private def isNumberOutOfRange(blockNumber: BigInt, bestBlockNumber: BigInt): Boolean =
    (blockNumber - bestBlockNumber > blockPoolConfig.maxBlockAhead) ||
      (bestBlockNumber - blockNumber > blockPoolConfig.maxBlockBehind)
}

object BlockPool {
  case class QueuedBlock(block: Block, totalDifficulty: Option[BigInt])
  case class Leaf(hash: ByteVector, totalDifficulty: BigInt)

  def apply[F[_]: ConcurrentEffect](history: History[F],
                                    blockPoolConfig: BlockPoolConfig = BlockPoolConfig()): F[BlockPool[F]] =
    for {
      blocks           <- Ref.of[F, Map[ByteVector, QueuedBlock]](Map.empty)
      parentToChildren <- Ref.of[F, Map[ByteVector, Set[ByteVector]]](Map.empty)
    } yield BlockPool(history, blockPoolConfig, blocks, parentToChildren)
}
