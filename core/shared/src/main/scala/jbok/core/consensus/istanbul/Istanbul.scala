package jbok.core.consensus.istanbul

import _root_.io.circe.generic.JsonCodec
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
import jbok.core.messages.IstanbulMessage
import jbok.core.peer.{PeerManager, PeerRoutes, PeerService, Request}
import scalacache.Cache
import fs2._

import scala.collection.mutable.{ArrayBuffer, Map => MMap}
import scala.concurrent.duration._


case class Istanbul[F[_]](
    config: IstanbulConfig,
    history: History[F],
    keyPair: KeyPair,
    roundChangePromise: Ref[F, Deferred[F, Int]],
    state: Ref[F, State],
    backlogs: Ref[F, Map[Address, List[IstanbulMessage]]],
    current: Ref[F, RoundState],
    roundChanges: Ref[F, Map[Int, MessageSet]],
    validatorSet: Ref[F, ValidatorSet],
    candidates: Ref[F, Map[Address, Boolean]]
)(implicit F: Concurrent[F], C: Cache[Snapshot], timer: Timer[F], chainId: BigInt) {

  def signer: Address = Address(keyPair)

  private[this] val log = org.log4s.getLogger("Istanbul core")

  def sign(bv: ByteVector): F[CryptoSignature] =
    Signature[ECDSA].sign[F](bv.kec256.toArray, keyPair, chainId)

  def ecrecover(hash: ByteVector, sig: CryptoSignature): Option[KeyPair.Public] =
    Signature[ECDSA].recoverPublic(hash.kec256.toArray, sig, chainId)

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

  def checkMessage(message: IstanbulMessage): F[CheckResult] =
    message.msgCode match {
      case IstanbulMessage.msgPreprepareCode =>
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
  def checkMessage(message: IstanbulMessage, view: View): F[CheckResult] =
    for {
      vs           <- validatorSet.get
      currentState <- current.get
      // FIXME: this check is not so correct because the message may be a old or future message, current validatorSet may not contain the history validator
      result <- if (!vs.contains(message.address)) {
        F.pure(CheckResult.UnauthorizedAddress)
      } else if (message.msgCode == IstanbulMessage.msgRoundChange) {
        // ROUND CHANGE message
        if (view.blockNumber > currentState.blockNumber) {
          // message's sequence bigger than current state
          F.pure(CheckResult.FutureMessage)
        } else if (view.blockNumber < currentState.blockNumber || (view.blockNumber == currentState.blockNumber && view.round < currentState.round)) {
          F.pure(CheckResult.OldMessage)
        } else {
          F.pure(CheckResult.Success)
        }
      } else if (view.blockNumber > currentState.blockNumber || (view.blockNumber == currentState.blockNumber && view.round > currentState.round)) {
        F.pure(CheckResult.FutureMessage)
      } else if (view.blockNumber < currentState.blockNumber || (view.blockNumber == currentState.blockNumber && view.round < currentState.round)) {
        F.pure(CheckResult.OldMessage)
      } else if (currentState.waitingForRoundChange) {
        F.pure(CheckResult.FutureMessage)
      } else {
        state.get.map {
          case StateNewRound =>
            if (message.msgCode > IstanbulMessage.msgPreprepareCode) CheckResult.FutureMessage else CheckResult.Success
          case _ => CheckResult.Success
        }
      }
    } yield result

  /**
    * store future message for process later
    */
  def storeBacklog(addr: Address, msg: IstanbulMessage): F[Unit] =
    backlogs.update(m => m + (addr -> (m.getOrElse(addr, List.empty[IstanbulMessage]) :+ msg)))

  /**
    * process messages in backlog,
    * stop processing if backlog is empty or the first message in queue is a future message
    */
  private def processBacklog(): F[Unit] = {
    def process(address: Address, messages: List[IstanbulMessage]): F[Unit] =
      messages match {
        case message :: tail =>
          for {
            checkResult <- checkMessage(message)
            _ <- checkResult match {
              case CheckResult.Success =>
                // remove message from backlogs
                backlogs.update(m => m + (address -> tail)) >>
                  handleMessage(message) >>
                  process(address, tail)
              case CheckResult.FutureMessage =>
                F.unit
              case _ =>
                backlogs.update(m => m + (address -> tail)) >>
                  process(address, tail)
            }
          } yield ()
        case _ => F.unit
      }
    for {
      logs <- backlogs.get
      _    <- logs.toList.map(backlog => process(backlog._1, backlog._2)).sequence
    } yield ()
  }

  /**
    * check and transform a IstanbulMessage to Action
    */
  private def checkAndTransform(message: IstanbulMessage): F[Either[Action, Throwable]] =
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

  private def transformAction(message: IstanbulMessage): F[Either[Action, Throwable]] =
    message.msgCode match {
      case IstanbulMessage.msgPreprepareCode => F.pure(Left(PreprepareAction(message)))
      case IstanbulMessage.msgPrepareCode    => F.pure(Left(PrepareAction(message)))
      case IstanbulMessage.msgCommitCode     => F.pure(Left(CommitAction(message)))
      case IstanbulMessage.msgRoundChange    => F.pure(Left(RoundChangeAction(message)))
      case _                                 => F.pure(Right(new Exception("invalid message")))
    }

  /**
    * handle received message
    * after process the received message, we need to process messages in backlog if it's possible
    */
  def handleMessage(message: IstanbulMessage): F[Unit] =
    for {
      action <- checkAndTransform(message)
      _ = log.debug(s"receive action:$action, message: $message")
      _ <- action match {
        case Left(a) =>
          transition(a) >>
            processBacklog()
        case Right(_) => F.unit
      }
    } yield ()

  def currentContext: StateContext[F] =
    StateContext(keyPair, validatorSet, current, state, roundChanges, roundChangePromise, sign, handleMessage)

  def transition(action: Action): F[Unit] =
    for {
      s <- state.get
      _ = log.debug(s"before state:$s")
      _ <- s.handle(action, currentContext)
      _ <- state.get.map(newState => log.debug(s"after state:$newState"))
    } yield ()

  /**
    * check the given round and lastProposal should be trigger a New proposal or not
    * return true means we are in old round, and we should work on current sequence
    * return false means the old round is passed, we should propose a new block and work on new sequence
    */
  private def shouldChangeRound(lastProposal: Block, round: Int): F[Boolean] =
    for {
      rs <- this.current.get
      roundChange <- if (lastProposal.header.number >= rs.blockNumber) F.pure(false)
      else if (lastProposal.header.number == rs.blockNumber - 1) {
        if (round < rs.round)
          F.raiseError(
            new Exception(s"New round should not be smaller than current round:new $round,current ${rs.round}"))
        else F.pure(true)
      } else F.pure(false)
    } yield roundChange

  /**
    *
    * start a new round when
    * 1.we have received 2F+1 roundChange messages,start a new round to exits the round change loop,calculates the new proposer
    * 2.last proposal has succeeded, start a new round to generate a new block proposal
    */
  def startNewRound(round: Int, preprepare: Option[Preprepare] = None, allowTimeout: Boolean = true): F[Unit] =
    /**
      * select proposal
      * calculate new proposer
      * set current RoundState,(round,sequence,validatorSet,waitingForRoundChange,etc.)
      * set current state to StateNewRound
      * reset roundChange messages
      */
    for {
      lastProposal <- history.getBestBlock
      context = currentContext
      roundChange  <- shouldChangeRound(lastProposal, round)
      currentState <- this.current.get
      view <- if (roundChange) {
        F.pure(View(blockNumber = currentState.blockNumber, round = round))
      } else {

        /**
          * calculate new ValidatorSet for this round
          */
        for {
          newValidators <- getValidators(lastProposal.header.number, lastProposal.header.hash)
          _             <- validatorSet.set(newValidators)
        } yield View(blockNumber = lastProposal.header.number + 1, round = 0)
      }

      _ <- roundChanges.set(Map.empty)
      proposer <- validatorSet.get.flatMap(
        _.calculateProposer(Istanbul.ecrecover(lastProposal.header), view.round, config.proposerPolicy) match {
          case Some(p) => F.pure[Address](p)
          case None    => F.raiseError[Address](new Exception("proposer not found"))
        })

      _ = log.debug(s"roundChange:$roundChange")
      _ <- if (roundChange) {
        context.updateCurrentRound(view.round)
      } else {
        context.current.update(
          _.copy(round = view.round,
                 blockNumber = view.blockNumber,
                 preprepare = preprepare,
                 lockedHash = None,
                 prepares = MessageSet.empty,
                 commits = MessageSet.empty))
      }
      _      <- roundChanges.set(Map.empty)
      _      <- validatorSet.update(vs => vs.copy(proposer = proposer))
      _      <- context.current.update(_.copy(waitingForRoundChange = false))
      _      <- context.setState(StateNewRound)
      valSet <- validatorSet.get
      _ <- if (valSet.isProposer(signer)) {
        for {
          proposal <- context.proposal
          _ = log.debug(s"proposal:$proposal")
          _ <- proposal match {
            case Some(block) => StateNewRound.handle(ProposeAction(block), context)
            case None        => F.unit
          }
        } yield ()
      } else F.unit

      _ <- if (allowTimeout) waitTimeout() else F.unit
    } yield ()

  private def waitTimeout(): F[Unit] =
    for {
      d      <- Deferred[F, Int]
      _      <- roundChangePromise.set(d)
      result <- d.get.timeout(config.requestTimeout.millis).attempt
      _ = log.debug(s"timeout result:$result")
      _ <- result match {
        case Left(_) =>
          // this means timeout
          for {
            vs           <- validatorSet.get
            currentState <- current.get
            rcs          <- roundChanges.get
            lastProposal <- history.getBestBlock
            context  = currentContext
            _        = log.debug(s"timeout:$context")
            maxRound = rcs.filter(_._2.messages.size > vs.f).toList.map(_._1).maximumOption.getOrElse(0)
            _ <- if (!currentState.waitingForRoundChange && maxRound > currentState.round) {

              /**
                * we're not waiting for round change yet.
                * if the validator has received ROUND CHANGE messages from its peers,
                * it picks the largest round number which has F + 1 of ROUND CHANGE messages.
                */
              Proxy.sendRoundChange(maxRound, context)
            } else {
              if (lastProposal.header.number >= currentState.blockNumber) {
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

  private[jbok] def extract(msg: ByteVector): F[IstanbulMessage] = F.delay {
    RlpCodec.decode[IstanbulMessage](msg.bits).require.value
  }
}

object Istanbul {

  val extraVanity: Int          = 32 // Fixed number of extra-data bytes reserved for validator vanity
  val extraSeal: Int            = 65 // Fixed number of extra-data bytes reserved for validator seal
  val uncleHash: ByteVector     = RlpCodec.encode(()).require.bytes.kec256 // Always Keccak256(RLP([]))
  val nonceAuthVote: ByteVector = hex"0xffffffffffffffff" // Magic nonce number to vote on adding a new signer
  val nonceDropVote: ByteVector = hex"0x0000000000000000" // Magic nonce number to vote on removing a signer.
  val mixDigest: ByteVector     = hex"0x63746963616c2062797a616e74696e65206661756c7420746f6c6572616e6365"

  def apply[F[_]](config: IstanbulConfig, history: History[F], keyPair: KeyPair, state: State)(
      implicit F: Concurrent[F],
      C: Cache[Snapshot],
      timer: Timer[F],
      chainId: BigInt): F[Istanbul[F]] =
    for {
      promise    <- Deferred[F, Int]
      refPromise <- Ref.of(promise)
      refState   <- Ref.of[F, State](state)
      backlogs   <- Ref.of[F, Map[Address, List[IstanbulMessage]]](Map.empty)
      rs = RoundState(0, 0, None, MessageSet.empty, MessageSet.empty, None, false)
      current      <- Ref.of[F, RoundState](rs)
      roundChanges <- Ref.of[F, Map[Int, MessageSet]](Map.empty)
      validatorSet <- Ref.of[F, ValidatorSet](ValidatorSet.empty)
      candidates   <- Ref.of[F, Map[Address, Boolean]](Map.empty)
    } yield
      new Istanbul[F](config,
                      history,
                      keyPair,
                      refPromise,
                      refState,
                      backlogs,
                      current,
                      roundChanges,
                      validatorSet,
                      candidates)

  def extractIstanbulExtra(header: BlockHeader): IstanbulExtra =
    RlpCodec.decode[IstanbulExtra](header.extraData.drop(Istanbul.extraVanity).bits).require.value

  /**
    * set committedSeals and seal to empty
    * return new header
    */
  def filteredHeader(header: BlockHeader, keepSeal: Boolean): BlockHeader = {
    val extra = extractIstanbulExtra(header)
    val newExtra = if (!keepSeal) {
      extra.copy(proposerSig = ByteVector.empty, committedSigs = List.empty)
    } else {
      extra.copy(committedSigs = List.empty)
    }
    val payload   = RlpCodec.encode(newExtra).require.bytes
    val newHeader = header.copy(extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ payload)
    newHeader
  }

  /**
    * set committedSeals and seal to empty
    * return new header's rlp
    */
  def sigHash(header: BlockHeader): ByteVector = {
    val newHeader = filteredHeader(header, false)
    RlpCodec.encode(newHeader).require.bytes
  }
  def ecrecover(header: BlockHeader): Address = {
    // Retrieve the signature from the header extra-data
    val signature = extractIstanbulExtra(header).proposerSig
    val hash      = sigHash(header).kec256
    val sig       = CryptoSignature(signature.toArray)
    val chainId   = ECDSAChainIdConvert.getChainId(sig.v)
    val public    = Signature[ECDSA].recoverPublic(hash.toArray, sig, chainId.get).get
    Address(public.bytes.kec256)
  }
}
