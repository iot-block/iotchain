package jbok.core.consensus.istanbul

import java.net.InetSocketAddress

import cats.data.OptionT
import cats.effect.{ConcurrentEffect, IO, Sync}
import cats.effect.concurrent.Ref
import cats.implicits._
import fs2.{Pipe, Stream}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.History
import jbok.core.models.{Address, Block, BlockHeader}
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, Signature}
import jbok.persistent.LruMap
import scodec.bits._

import scala.collection.mutable.{Map => MMap, Set => MSet}

case class IstanbulExtra(
    validators: List[Address],
    seal: ByteVector,
    committedSeals: List[ByteVector]
)

case class Istanbul[F[_]](
    val config: IstanbulConfig,
    val history: History[F],
    val recents: LruMap[ByteVector, Snapshot],
    val candidates: MMap[Address, Boolean],
    val signer: Address
)(implicit F: ConcurrentEffect[F]) {

  private[this] val log = org.log4s.getLogger("Istanbul")

  private val waitingForRoundChange: Ref[F, Boolean] = Ref.unsafe[F, Boolean](false)
  private val current: Ref[F, Option[RoundState]]    = Ref.unsafe[F, Option[RoundState]](Option.empty)
  private val validatorSet: Ref[F, ValidatorSet]     = Ref.unsafe[F, ValidatorSet](ValidatorSet.empty)
  private val roundChangeSet: Ref[F, RoundChangeSet] =
    Ref.unsafe[F, RoundChangeSet](RoundChangeSet(ValidatorSet.empty, MMap.empty))

  def readSnapshot(number: BigInt, hash: ByteVector): OptionT[F, Snapshot] = {
    // try to read snapshot from cache or db
    log.trace(s"try to read snapshot(${number}) from cache")
    OptionT
      .fromOption[F](recents.get(hash)) // If an in-memory snapshot was found, use that
      .orElseF(
        if (number % config.checkpointInterval == 0) {
          // If an on-disk checkpoint snapshot can be found, use that
          log.trace(s"not found in cache, try to read snapshot(${number}) from db")
          Snapshot.loadSnapshot[F](history.db, hash)
        } else {
          log.trace(s"snapshot(${number}) not found in cache and db")
          F.pure(None)
        }
      )
  }

  def genesisSnapshot: F[Snapshot] = {
    log.trace(s"making a genesis snapshot")
    for {
      genesis <- history.genesisHeader
      n     = (genesis.extraData.length - Istanbul.extraVanity).toInt / 20
      extra = Istanbul.extractIstanbulExtra(genesis)
      snap  = Snapshot(config, 0, genesis.hash, ValidatorSet(extra.validators))
      _ <- Snapshot.storeSnapshot[F](snap, history.db)
      _ = log.trace(s"stored genesis with ${extra.validators.size} validators")
    } yield snap
  }

  private[jbok] def snapshot(number: BigInt,
                             hash: ByteVector,
                             parents: List[BlockHeader],
                             headers: List[BlockHeader] = Nil): F[Snapshot] = {
    val snap = readSnapshot(number, hash)
      .orElseF(if (number == 0) genesisSnapshot.map(_.some) else F.pure(None))

    snap.value flatMap {
      case Some(s) =>
        // Previous snapshot found, apply any pending headers on top of it
        log.trace(s"applying ${headers.length} headers")
        val newSnap = Snapshot.applyHeaders(s, headers)
        recents.put(newSnap.hash, newSnap)
        // If we've generated a new checkpoint snapshot, save to disk
        if (newSnap.number % config.checkpointInterval == 0 && headers.nonEmpty) {
          Snapshot.storeSnapshot[F](newSnap, history.db).map(_ => newSnap)
        } else {
          F.pure(newSnap)
        }
      case None => // No snapshot for this header, gather the header and move backward(recur)
        for {
          (h, p) <- if (parents.nonEmpty) {
            // If we have explicit parents, pick from there (enforced)
            F.pure((parents.last, parents.slice(0, parents.length - 1)))
          } else {
            // No explicit parents (or no more left), reach out to the database
            history.getBlockHeaderByHash(hash).map(header => header.get -> parents)
          }
          snap <- snapshot(number - 1, h.parentHash, p, h :: headers)
        } yield snap
    }
  }

  private[jbok] def extract(msg: ByteVector): F[Message] = F.delay {
    RlpCodec.decode[Message](msg.bits).require.value
  }
//  val pipe: Pipe[F, (InetSocketAddress, ByteVector), Unit] = { input =>
//    val output = input
//      .evalMap {
//        case (remote, msg) => extract(msg)
//      }
//      .evalMap[F, Unit](handle(_))
//
//    output
//      .flatMap(xs => Stream(xs: _*).covary[F])
//      .evalMap {
//        case (remote, kad) => encode(kad).map(udp => remote -> udp)
//      }
//  }

  private def handle(msg: Message): F[Unit] =
    msg.code match {
      case Message.msgPreprepareCode => handlePreprepare(msg)
      case Message.msgPrepareCode    => handlePrepare(msg)
      case _                         => F.unit
    }

  private def handlePrepare(msg: Message): F[Unit] =
    F.unit
  private def handlePreprepare(msg: Message): F[Unit] =
    broadcast(Message(Message.msgPrepareCode))

  private def broadcast(msg: Message): F[Unit] =
    F.unit

//  def start(): F[Unit] =
//    startNewRound(0)

  private def shouldChangeRound(lastProposal: Block, round: BigInt): F[Boolean] =
    for {
      currentState <- this.current.get
      roundChange <- currentState match {
        case Some(rs) if lastProposal.header.number >= rs.sequence                         => F.pure(false)
        case Some(rs) if lastProposal.header.number == rs.sequence - 1 && round > rs.round => F.pure(true)
        case Some(rs) if lastProposal.header.number == rs.sequence - 1 =>
          F.raiseError(
            new Exception(s"New round should not be smaller than current round:new ${round},current ${rs.round}"))
        case Some(rs) => F.raiseError(new Exception("New sequence should be larger than current sequence"))
        case _        => F.pure(false)
      }
    } yield roundChange

  def getValidators(number: BigInt, hash: ByteVector): F[ValidatorSet] =
    snapshot(number, hash, List.empty).map(_.validatorSet)

//  set RoundState for new round, with checking block is locked or not
//  def updateRoundState(view: View, roundChange: Boolean): F[Unit] =
//    for {
//      state  <- this.current.get
//      valSet <- this.validatorSet.get
//      (preprepare, proposal, lockedHash) = if (roundChange && state.isDefined) {
//        if (state.get.isLocked()) {
//          (state.get.preprepare, state.get.proposal, state.get.lockedHash)
//        } else {
//          (None, state.get.proposal, ByteVector.empty)
//        }
//      } else {
//        (None, None, ByteVector.empty)
//      }
//      _ = this.current.update(
//        rs =>
//          Option(
//            RoundState(
//              view.round,
//              view.sequence,
//              preprepare,
//              MessageSet.empty.copy(validatorSet = valSet),
//              MessageSet.empty.copy(validatorSet = valSet),
//              lockedHash,
//              proposal
//            )))
//    } yield ()
//
//  def startNewRound(round: BigInt): F[Unit] = {
//    for {
//      lastProposal <- history.getBestBlock
//      roundChange  <- shouldChangeRound(lastProposal, round)
//      currentState <- this.current.get
//      view = if (roundChange) {
//        View(sequence = currentState.get.sequence, round = round)
//      } else {
//        getValidators(lastProposal.header.number, lastProposal.header.hash).map(validatorSet.set(_))
//        View(sequence = lastProposal.header.number + 1, round = 0)
//      }
//
//      valSet <- validatorSet.get
//      _      <- roundChangeSet.set(RoundChangeSet(valSet, MMap.empty)) //reset RoungChange messages
//      proposer <- valSet.calculateProposer(Istanbul.ecrecover(lastProposal.header), view.round, config.proposerPolicy) match {
//        case Some(p) => F.pure(p)
//        case None    => F.raiseError(new Exception("proposer not found"))
//      }
//      _ <- updateRoundState(view, roundChange)
//      _ = validatorSet.update(vs => vs.copy(proposer = proposer))
//      _ = waitingForRoundChange.set(false)
//    } yield ()
//
//    F.unit
//  }

}

object Istanbul {
  val extraVanity   = 32 // Fixed number of extra-data bytes reserved for validator vanity
  val extraSeal     = 65 // Fixed number of extra-data bytes reserved for validator seal
  val uncleHash     = RlpCodec.encode(()).require.bytes.kec256 // Always Keccak256(RLP([]))
  val nonceAuthVote = hex"0xffffffffffffffff" // Magic nonce number to vote on adding a new signer
  val nonceDropVote = hex"0x0000000000000000" // Magic nonce number to vote on removing a signer.
  val mixDigest     = hex"0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"

  def extractIstanbulExtra(header: BlockHeader): IstanbulExtra =
    RlpCodec.decode[IstanbulExtra](header.extraData.bits.drop(Istanbul.extraVanity)).require.value

  def sigHash(header: BlockHeader): ByteVector = {
    val bytes = RlpCodec.encode(header.copy(extraData = header.extraData.drop(extraVanity))).require.bytes
    bytes.kec256
  }
  def ecrecover(header: BlockHeader): Address = {
    // Retrieve the signature from the header extra-data
    val signature = header.extraData.drop(extraVanity)
    val hash      = sigHash(header)
    val sig       = CryptoSignature(signature.toArray)
    val public    = Signature[ECDSA].recoverPublic(hash.toArray, sig, None).get
    Address(public.bytes.kec256)
  }

}
