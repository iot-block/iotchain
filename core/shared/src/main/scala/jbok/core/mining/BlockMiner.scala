package jbok.core.mining

import cats.effect.{Clock, ConcurrentEffect}
import cats.implicits._
import fs2._
import fs2.concurrent.SignallingRef
import jbok.codec.rlp.implicits._
import jbok.core.ledger.{BlockResult, BloomFilter}
import jbok.core.models._
import jbok.core.peer.PeerSet
import jbok.core.store.namespaces
import jbok.core.sync.BlockHandler
import jbok.core.utils.ByteUtils
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.KeyValueDB
import scodec.Codec
import scodec.bits.ByteVector

import scala.concurrent.duration.MILLISECONDS

case class BlockPreparationResult[F[_]](block: Block, blockResult: BlockResult[F], stateRootHash: ByteVector)

final class BlockMiner[F[_]](
    val blockHandler: BlockHandler[F],
    val stopWhenTrue: SignallingRef[F, Boolean]
)(implicit F: ConcurrentEffect[F], clock: Clock[F]) {
  private[this] val log = org.log4s.getLogger("BlockMiner")

  val executor  = blockHandler.executor
  val consensus = executor.consensus
  val history   = executor.history

  // sort and truncate transactions
  def prepareTransactions(stxs: List[SignedTransaction], blockGasLimit: BigInt): F[List[SignedTransaction]] = {
    val sortedByPrice = stxs
      .groupBy(stx => SignedTransaction.getSender(stx).getOrElse(Address.empty))
      .values
      .toList
      .flatMap { txsFromSender =>
        val ordered = txsFromSender
          .sortBy(-_.gasPrice)
          .sortBy(_.nonce)
          .foldLeft(List.empty[SignedTransaction]) {
            case (acc, tx) =>
              if (acc.exists(_.nonce == tx.nonce)) {
                acc
              } else {
                acc :+ tx
              }
          }
          .takeWhile(_.gasLimit <= blockGasLimit)
        ordered.headOption.map(_.gasPrice -> ordered)
      }
      .sortBy { case (gasPrice, _) => -gasPrice }
      .flatMap { case (_, txs) => txs }

    val transactionsForBlock = sortedByPrice
      .scanLeft(BigInt(0), None: Option[SignedTransaction]) {
        case ((accGas, _), stx) => (accGas + stx.gasLimit, Some(stx))
      }
      .collect { case (gas, Some(stx)) => (gas, stx) }
      .takeWhile { case (gas, _) => gas <= blockGasLimit }
      .map { case (_, stx) => stx }

    F.pure(transactionsForBlock)
  }

  // prepare block by executing the block transactions
  def prepareBlock(header: BlockHeader, body: BlockBody): F[BlockPreparationResult[F]] = {
    val block = Block(header, body)
    for {
      start      <- clock.monotonic(MILLISECONDS)
      (br, stxs) <- executor.executeBlockTransactions(block, shortCircuit = false)
      finish     <- clock.monotonic(MILLISECONDS)
      _ = log.debug(s"execute prepared block(${block.header.number}) within ${finish - start}ms")
      worldToPersist <- executor.payReward(block, br.worldState)
      worldPersisted <- worldToPersist.persisted
    } yield {
      BlockPreparationResult(
        block.copy(body = block.body.copy(transactionList = stxs)),
        br,
        worldPersisted.stateRootHash
      )
    }
  }

  // generate a new block with specified transactions and ommers
  def generateBlock(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None,
      ommersOpt: Option[List[BlockHeader]] = None
  ): F[Block] =
    for {
      parent <- parentOpt.fold(history.getBestBlock)(F.pure)
      stxs   <- stxsOpt.fold(blockHandler.txPool.getPendingTransactions.map(_.map(_.stx)))(F.pure)
      ommers <- ommersOpt.fold(blockHandler.ommerPool.getOmmers(parent.header.number + 1))(F.pure)
      header <- executor.consensus.prepareHeader(parent, ommers)
      _ = log.debug(s"prepared header for block(${parent.header.number + 1})")
      txs <- prepareTransactions(stxs, header.gasLimit)
      _ = log.debug(s"prepared ${txs.length} tx(s) for block(${parent.header.number + 1})")
      prepared         <- prepareBlock(header, BlockBody(txs, ommers))
      transactionsRoot <- calcMerkleRoot(prepared.block.body.transactionList)
      receiptsRoot     <- calcMerkleRoot(prepared.blockResult.receipts)
    } yield {
      prepared.block.copy(
        header = prepared.block.header.copy(
          transactionsRoot = transactionsRoot,
          stateRoot = prepared.stateRootHash,
          receiptsRoot = receiptsRoot,
          logsBloom =
            ByteUtils.or(BloomFilter.EmptyBloomFilter +: prepared.blockResult.receipts.map(_.logsBloomFilter): _*),
          gasUsed = prepared.blockResult.gasUsed
        )
      )
    }

  // mine a prepared block
  def mine(block: Block): F[Option[Block]] =
    executor.consensus.mine(block).attempt.map {
      case Left(e) =>
        log.error(e)(s"mining for block(${block.header.number}) failed")
        None
      case Right(b) =>
        Some(b)
    }

  // submit a newly mined block
  def submitNewBlock(block: Block): F[Unit] = {
    log.info(s"${block.tag} successfully mined, submit to BlockHandler")
    blockHandler
      .handleBlock(block, PeerSet.empty)
      .flatMap(_.traverse { case (peer, message) => peer.conn.write(message) }.void)
  }

  def mineAndSubmit(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None,
      ommersOpt: Option[List[BlockHeader]] = None
  ): F[Option[Block]] =
    for {
      block <- generateBlock(parentOpt, stxsOpt, ommersOpt)
      _ = log.info(s"${block.tag} prepared for mining")
      minedOpt <- mine(block)
      _        <- minedOpt.fold(F.unit)(submitNewBlock)
    } yield minedOpt

  def miningStream(): Stream[F, Block] =
    Stream
      .repeatEval(mineAndSubmit())
      .unNone
      .onFinalize(stopWhenTrue.set(true))

  def start: F[Unit] =
    stopWhenTrue.get.flatMap {
      case true  => stopWhenTrue.set(false) *> F.start(miningStream().interruptWhen(stopWhenTrue).compile.drain).void
      case false => F.unit
    }

  def stop: F[Unit] =
    stopWhenTrue.set(true)

  def isMining: F[Boolean] = stopWhenTrue.get.map(!_)

  //////////////////////////////
  //////////////////////////////

  private[jbok] def calcMerkleRoot[V: Codec](entities: List[V]): F[ByteVector] =
    for {
      db   <- KeyValueDB.inmem[F]
      mpt  <- MerklePatriciaTrie[F](namespaces.empty, db)
      _    <- entities.zipWithIndex.map { case (v, k) => mpt.put[Int, V](k, v, namespaces.empty) }.sequence
      root <- mpt.getRootHash
    } yield root
}

object BlockMiner {
  def apply[F[_]: ConcurrentEffect](
      synchronizer: BlockHandler[F]
  )(implicit clock: Clock[F]): F[BlockMiner[F]] = SignallingRef[F, Boolean](true).map { stopWhenTrue =>
    new BlockMiner[F](
      synchronizer,
      stopWhenTrue
    )
  }
}
