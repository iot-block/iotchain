package jbok.core.consensus.istanbul

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.{ConcurrentEffect, IO, Sync}
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.models.{Address, Block, BlockHeader}
import scodec.bits.ByteVector
import cats.implicits._
import jbok.crypto._
import scodec.bits._
import jbok.core.History
import jbok.crypto.signature.{CryptoSignature, ECDSA, Signature}
import jbok.persistent.LruMap

import scala.collection.mutable.{ArrayBuffer, Map => MMap}

case class Core[F[_]](config: IstanbulConfig,
                      history: History[F],
                      recents: LruMap[ByteVector, Snapshot],
                      candidates: MMap[Address, Boolean],
                      backlogs: Ref[F, MMap[Address, ArrayBuffer[Message]]],
                      signer: Address,
                      current: Ref[F, RoundState],
                      roundChanges: Ref[F, MMap[BigInt, MessageSet]],
                      validatorSet: Ref[F, ValidatorSet],
                      state: Ref[F, State])(implicit F: Sync[F]) {

  private[this] val log = org.log4s.getLogger("Istanbul core")

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

  def getValidators(number: BigInt, hash: ByteVector): F[ValidatorSet] =
    snapshot(number, hash, List.empty).map(_.validatorSet)

  def checkMessage(message: Message): F[CheckResult] =
    message.code match {
      case Message.msgPreprepareCode => {
        val preprepare = RlpCodec.decode[Preprepare](message.msg.bits).require.value
        checkMessage(message, preprepare.view)
      }
      case _ => {
        val subject = RlpCodec.decode[Subject](message.msg.bits).require.value
        checkMessage(message, subject.view)
      }
    }

  /**
    * this function make sure the message for processing later is in CURRENT sequence
    * if the message's SEQUENCE is not equal than CURRENT SEQUENCE, this function will never return CheckResult.Success
    */
  def checkMessage(message: Message, view: View): F[CheckResult] =
    for {
      vs           <- validatorSet.get
      currentState <- current.get
//      FIXME: this check is not so right because the message may be a old message, current validatorSet may not contain the history validator
      result <- if (!vs.contains(message.address)) {
        F.pure(CheckResult.UnauthorizedAddress)
      } else if (message.code == Message.msgRoundChange) {
//        ROUND CHANGE message
        if (view.sequence > currentState.sequence) {
//          message's sequence bigger than current state
          F.pure(CheckResult.FutureMessage)
        } else if (view.sequence < currentState.sequence || (view.sequence == currentState.sequence && view.round < currentState.round)) {
          F.pure(CheckResult.OldMessage)
        } else {
          F.pure(CheckResult.Success)
        }
      } else if (view.sequence > currentState.sequence || (view.sequence == currentState.sequence && view.round > currentState.round)) {
        F.pure(CheckResult.FutureMessage)
      } else if (view.sequence < currentState.sequence || (view.sequence == currentState.sequence && view.round < currentState.round)) {
        F.pure(CheckResult.OldMessage)
      } else if (currentState.waitingForRoundChange) {
        F.pure(CheckResult.FutureMessage)
      } else {
        state.get.map(s =>
          s match {
            case StateNewRound => {
              if (message.code > Message.msgPreprepareCode) CheckResult.FutureMessage else CheckResult.Success
            }
            case _ => CheckResult.Success
        })
      }
    } yield result

  //  store future message for process later
  def storeBacklog(address: Address, message: Message): F[Unit] =
    backlogs.get.map(_.getOrElseUpdate(address, ArrayBuffer.empty).append(message))

  private def processBacklog(): Unit = {}

  private def checkAndTransform(message: Message): F[Either[Action, Throwable]] =
    for {
      result <- checkMessage(message)
      action <- result match {
        case CheckResult.Success => {
          transformAction(message)
        }
        case CheckResult.FutureMessage => {
          storeBacklog(message.address, message)
          F.pure(Right(new Exception("future message")))
        }
        case _ => F.pure(Right(new Exception("check message error")))
      }
    } yield action

  private def transformAction(message: Message): F[Either[Action, Throwable]] =
    message.code match {
      case Message.msgPreprepareCode => F.pure(Left(PreprepareAction(message)))
      case Message.msgPrepareCode    => F.pure(Left(PrepareAction(message)))
      case Message.msgCommitCode     => F.pure(Left(CommitAction(message)))
      case Message.msgRoundChange    => F.pure(Left(RoundChangeAction(message)))
      case _                         => F.pure(Right(new Exception("invalid message")))
    }

  def handleMessage(message: Message): F[Unit] =
    for {
      action <- checkAndTransform(message)
      _ <- action match {
        case Left(a)  => transition(a)
        case Right(e) => F.unit
      }
    } yield ()

  def transition(action: Action): F[Unit] =
    for {
      s <- state.get
      context = StateContext(signer, validatorSet, current, state, roundChanges, startNewRound)
      _ <- s.handle(action, context)
    } yield ()

  private def shouldChangeRound(lastProposal: Block, round: BigInt): F[Boolean] =
    for {
      rs <- this.current.get
      roundChange <- if (lastProposal.header.number >= rs.sequence) F.pure(false)
      else if (lastProposal.header.number == rs.sequence - 1) {
        if (round < rs.round)
          F.raiseError(
            new Exception(s"New round should not be smaller than current round:new ${round},current ${rs.round}"))
        else F.pure(true)
      } else F.pure(false)
    } yield roundChange

  def startNewRound(round: BigInt, context: StateContext[F]): F[Unit] =
    /**
      * set current RoundState,(round,sequence,validatorSet,waitingForRoundChange,etc.)
      * set current state to StateNewRound
      * reset roundChange messages
      */
    for {
      lastProposal <- history.getBestBlock
      roundChange  <- shouldChangeRound(lastProposal, round)
      currentState <- this.current.get
      view = if (roundChange) {
        View(sequence = currentState.sequence, round = round)
      } else {

        /**
          * calculate new ValidatorSet for this round
          */
        getValidators(lastProposal.header.number, lastProposal.header.hash).map(validatorSet.set(_))
        View(sequence = lastProposal.header.number + 1, round = 0)
      }

      _ <- roundChanges.set(MMap.empty)
      proposer <- validatorSet.get.flatMap(
        _.calculateProposer(Istanbul.ecrecover(lastProposal.header), view.round, config.proposerPolicy) match {
          case Some(p) => F.pure[Address](p)
          case None    => F.raiseError[Address](new Exception("proposer not found"))
        })
      _ <- if (roundChange) {
        context.updateCurrentRound(view.round)
      } else {
        context.current.update(
          _.copy(round = view.round,
                 sequence = view.sequence,
                 preprepare = None,
                 lockedHash = ByteVector.empty,
                 prepares = MessageSet.empty,
                 commits = MessageSet.empty))
      }
      _      <- roundChanges.set(MMap.empty)
      _      <- validatorSet.update(vs => vs.copy(proposer = proposer))
      _      <- context.current.update(_.copy(waitingForRoundChange = false))
      _      <- context.setState(StateNewRound)
      valSet <- validatorSet.get
      _ <- if (roundChange && valSet.isProposer(signer)) {
        for {
          proposal <- context.proposal
          _ <- proposal match {
            case Some(block) => StateNewRound.handle(ProposeAction(block), context)
            case None        => F.unit
          }
        } yield ()
      } else F.unit
    } yield ()

}

object Core {
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
