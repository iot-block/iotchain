package jbok.core.consensus.istanbul

import cats.data.OptionT
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.models.{Address, Block, BlockHeader}
import scodec.bits.ByteVector
import cats.implicits._
import cats.effect.implicits._
import jbok.crypto._
import scodec.bits._
import jbok.crypto.signature._
import jbok.core.ledger.History
import scalacache.Cache

import scala.collection.mutable.{ArrayBuffer, Map => MMap}
import scala.concurrent.duration._

case class Istanbul[F[_]](config: IstanbulConfig,
                          history: History[F],
                          candidates: MMap[Address, Boolean],
                          backlogs: Ref[F, MMap[Address, ArrayBuffer[Message]]],
                          keyPair: KeyPair,
                          current: Ref[F, RoundState],
                          roundChanges: Ref[F, MMap[BigInt, MessageSet]],
                          validatorSet: Ref[F, ValidatorSet],
                          roundChangePromise: Ref[F, Deferred[F, BigInt]],
                          state: Ref[F, State])(implicit F: Concurrent[F], C: Cache[Snapshot], timer: Timer[F]) {

  private[this] val log = org.log4s.getLogger("Istanbul core")

//  def readSnapshot(number: BigInt, hash: ByteVector): OptionT[F, Snapshot] = {
//    // try to read snapshot from cache or db
//    log.trace(s"try to read snapshot(${number}) from cache")
//    OptionT
//      .fromOption[F](recents.get(hash)) // If an in-memory snapshot was found, use that
//      .orElseF(
//        if (number % config.checkpointInterval == 0) {
//          // If an on-disk checkpoint snapshot can be found, use that
//          log.trace(s"not found in cache, try to read snapshot(${number}) from db")
//          Snapshot.loadSnapshot[F](history.db, hash)
//        } else {
//          log.trace(s"snapshot(${number}) not found in cache and db")
//          F.pure(None)
//        }
//      )
//  }

  def genesisSnapshot: F[Snapshot] = {
    log.trace(s"making a genesis snapshot")
    for {
      genesis <- history.genesisHeader
      extra = Istanbul.extractIstanbulExtra(genesis)
      snap  = Snapshot(config, 0, genesis.hash, ValidatorSet(extra.validators))
      _ <- Snapshot.storeSnapshot[F](snap, history.db, config.epoch)
      _ = log.trace(s"stored genesis with ${extra.validators.size} validators")
    } yield snap
  }

  def applyHeaders(
      number: BigInt,
      hash: ByteVector,
      parents: List[BlockHeader],
      headers: List[BlockHeader] = Nil
  ): F[Snapshot] = {
    val snap =
      OptionT(Snapshot.loadSnapshot[F](history.db, hash))
        .orElseF(if (number == 0) genesisSnapshot.map(_.some) else F.pure(None))

    snap.value flatMap {
      case Some(s) =>
        // Previous snapshot found, apply any pending headers on top of it
        log.trace(s"applying ${headers.length} headers")
        for {
          newSnap <- Snapshot.applyHeaders[F](s, headers)
          _       <- Snapshot.storeSnapshot[F](newSnap, history.db, config.epoch)
        } yield newSnap

      case None =>
        // No snapshot for this header, gather the header and move backward(recur)
        for {
          (h, p) <- if (parents.nonEmpty) {
            // If we have explicit parents, pick from there (enforced)
            F.pure((parents.last, parents.slice(0, parents.length - 1)))
          } else {
            // No explicit parents (or no more left), reach out to the database
            history.getBlockHeaderByHash(hash).map(header => header.get -> parents)
          }
          snap <- applyHeaders(number - 1, h.parentHash, p, h :: headers)
        } yield snap
    }
  }

  def getValidators(number: BigInt, hash: ByteVector): F[ValidatorSet] =
    applyHeaders(number, hash, List.empty, List.empty).map(_.validatorSet)

  def checkMessage(message: Message): F[CheckResult] =
    message.code match {
      case Message.msgPreprepareCode =>
        val preprepare = RlpCodec.decode[Preprepare](message.msg.bits).require.value
        checkMessage(message, preprepare.view)
      case _ =>
        val subject = RlpCodec.decode[Subject](message.msg.bits).require.value
        checkMessage(message, subject.view)
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
        state.get.map {
          case StateNewRound =>
            if (message.code > Message.msgPreprepareCode) CheckResult.FutureMessage else CheckResult.Success
          case _ => CheckResult.Success
        }
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
        case CheckResult.Success =>
          transformAction(message)
        case CheckResult.FutureMessage =>
          storeBacklog(message.address, message).as(Right(new Exception("future message")))
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

//  def start():F[Unit] = {
//    for {
//
//    }yield()
//  }

  def handleMessage(message: Message): F[Unit] =
    for {
      action <- checkAndTransform(message)
      _ <- action match {
        case Left(a)  => transition(a)
        case Right(_) => F.unit
      }
    } yield ()

  def transition(action: Action): F[Unit] =
    for {
      s <- state.get
      context = StateContext(keyPair, validatorSet, current, state, roundChanges, roundChangePromise)
      _ <- s.handle(action, context)
    } yield ()

  private def shouldChangeRound(lastProposal: Block, round: BigInt): F[Boolean] =
    for {
      rs <- this.current.get
      roundChange <- if (lastProposal.header.number >= rs.sequence) F.pure(false)
      else if (lastProposal.header.number == rs.sequence - 1) {
        if (round < rs.round)
          F.raiseError(
            new Exception(s"New round should not be smaller than current round:new $round,current ${rs.round}"))
        else F.pure(true)
      } else F.pure(false)
    } yield roundChange

  def startNewRound(round: BigInt): F[Unit] =
    /**
      * set current RoundState,(round,sequence,validatorSet,waitingForRoundChange,etc.)
      * set current state to StateNewRound
      * reset roundChange messages
      */
    for {
      lastProposal <- history.getBestBlock
      context = StateContext(keyPair, validatorSet, current, state, roundChanges, roundChangePromise)
      roundChange  <- shouldChangeRound(lastProposal, round)
      currentState <- this.current.get
      view <- if (roundChange) {
        F.pure(View(sequence = currentState.sequence, round = round))
      } else {

        /**
          * calculate new ValidatorSet for this round
          */
        getValidators(lastProposal.header.number, lastProposal.header.hash)
          .map(validatorSet.set)
          .as(View(sequence = lastProposal.header.number + 1, round = 0))
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
      _ <- if (roundChange) {
        if (valSet.isProposer(Address(keyPair))) {
          for {
            proposal <- context.proposal
            _ <- proposal match {
              case Some(block) => StateNewRound.handle(ProposeAction(block), context)
              case None        => F.unit
            }
          } yield ()
        } else F.unit
      } else F.unit

      _ <- waitTimeout()
    } yield ()

  private def waitTimeout(): F[Unit] =
    for {
      d      <- Deferred[F, BigInt]
      _      <- roundChangePromise.set(d)
      result <- d.get.timeout(config.requestTimeout.millis).attempt
      _ <- result match {
        case Left(_) =>
          // this means timeout
          for {
            vs           <- validatorSet.get
            currentState <- current.get
            rcs          <- roundChanges.get
            lastProposal <- history.getBestBlock
            context  = StateContext(keyPair, validatorSet, current, state, roundChanges, roundChangePromise)
            maxRound = rcs.filter(_._2.messages.size > vs.f).toList.map(_._1).maximumOption.getOrElse(BigInt(0))
            _ <- if (!currentState.waitingForRoundChange && maxRound > currentState.round) {

              /**
                * we're not waiting for round change yet.
                * if the validator has received ROUND CHANGE messages from its peers,
                * it picks the largest round number which has F + 1 of ROUND CHANGE messages.
                */
              Proxy.sendRoundChange(maxRound, context)
            } else {
              if (lastProposal.header.number >= currentState.sequence) {
                // this means timeout and we receives verified block(s) through peer synchronization,
                // in this case we must start a new round
                startNewRound(0)
              } else {
                // otherwise, it picks 1 + current round number as the proposed round number.
                Proxy.sendNextRound(context)
              }
            }
          } yield ()
        case Right(round) => startNewRound(round)
      }
    } yield ()

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
}

object Istanbul {

  val extraVanity   = 32 // Fixed number of extra-data bytes reserved for validator vanity
  val extraSeal     = 65 // Fixed number of extra-data bytes reserved for validator seal
  val uncleHash     = RlpCodec.encode(()).require.bytes.kec256 // Always Keccak256(RLP([]))
  val nonceAuthVote = hex"0xffffffffffffffff" // Magic nonce number to vote on adding a new signer
  val nonceDropVote = hex"0x0000000000000000" // Magic nonce number to vote on removing a signer.
  val mixDigest     = hex"0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"

  def extractIstanbulExtra(header: BlockHeader): IstanbulExtra =
    RlpCodec.decode[IstanbulExtra](header.extraData.drop(Istanbul.extraVanity).bits).require.value

  def filteredHeader(header: BlockHeader, keepSeal: Boolean): BlockHeader = {
    val extra = extractIstanbulExtra(header)
    val newExtra = if (!keepSeal) {
      extra.copy(seal = ByteVector.empty, committedSeals = List.empty)
    } else {
      extra.copy(committedSeals = List.empty)
    }
    val payload   = RlpCodec.encode(newExtra).require.bytes
    val newHeader = header.copy(extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ payload)
    newHeader
  }

  def sigHash(header: BlockHeader): ByteVector = {
    val istanbulExtra = Istanbul.extractIstanbulExtra(header)
    val newHeaderPayload =
      RlpCodec.encode(istanbulExtra.copy(committedSeals = List.empty, seal = ByteVector.empty)).require.bytes
    val newHeader = header.copy(extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ newHeaderPayload)
    RlpCodec.encode(newHeader).require.bytes
  }
  def ecrecover(header: BlockHeader): Address = {
    // Retrieve the signature from the header extra-data
    val signature = extractIstanbulExtra(header).seal
    val hash      = sigHash(header).kec256
    val sig       = CryptoSignature(signature.toArray)
    val chainId   = ECDSAChainIdConvert.getChainId(sig.v)
    val public    = Signature[ECDSA].recoverPublic(hash.toArray, sig, chainId.get).get
    Address(public.bytes.kec256)
  }
}
