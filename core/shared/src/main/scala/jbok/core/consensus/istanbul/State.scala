package jbok.core.consensus.istanbul

import cats.effect.Concurrent
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import cats.implicits._
import jbok.core.messages.IstanbulMessage
import jbok.core.models.Block
import jbok.crypto.signature.CryptoSignature

sealed trait State {
  val log = org.log4s.getLogger("State")

  def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit]

  /**
    * check the message payload subject is equal than current subject
    */
  private def checkSubject[F[_]](message: IstanbulMessage, context: StateContext[F])(
      implicit F: Concurrent[F]): F[Boolean] =
    for {
      subject <- context.currentSubject()
      result <- subject match {
        case Some(s) =>
          F.delay(RlpCodec.decode[Subject](message.msg.bits).require.value)
            .map(
              prepare =>
                prepare.view.blockNumber == s.view.blockNumber
                  && prepare.view.round == s.view.round
                  && prepare.digest == s.digest)
        case None => F.pure(false)
      }
    } yield result

  def checkPrepare[F[_]](message: IstanbulMessage, context: StateContext[F])(implicit F: Concurrent[F]): F[Boolean] =
    checkSubject(message, context)

  def checkCommit[F[_]](message: IstanbulMessage, context: StateContext[F])(implicit F: Concurrent[F]): F[Boolean] =
    checkSubject(message, context)

//  TODO: insert block
  def insertBlock[F[_]](block: Block, committedSeals: List[CryptoSignature])(implicit F: Concurrent[F]): F[Boolean] =
    F.pure(true)

  def acceptPrepare[F[_]](message: IstanbulMessage, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      check <- checkPrepare(message, context)
      _     <- if (check) context.addPrepare(message) else F.unit
    } yield ()

  def acceptCommit[F[_]](message: IstanbulMessage, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      check <- checkCommit(message, context)
      _     <- if (check) context.addCommit(message) else F.unit
    } yield ()

  def handleCommitAction[F[_]](message: IstanbulMessage, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      check <- checkCommit(message, context)
      _ <- if (check) {
        for {
          _     <- context.addCommit(message)
          ready <- context.commitReady

          _ <- if (ready) {

            /**
              * when we receive 2F+1 COMMIT message,
              * set state to COMMITTED and trigger a InsertBlockAction directly
              */
            context.setState(StateCommitted) >>
              StateCommitted.handle(InsertBlockAction, context)
          } else F.unit
        } yield ()
      } else F.unit
    } yield ()

  def handleRoundChange[F[_]](message: IstanbulMessage, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      payload      <- F.delay(RlpCodec.decode[Subject](message.msg.bits).require.value)
      _            <- context.addRoundChange(payload.view.round, message)
      roundState   <- context.current.get
      validatorSet <- context.validatorSet.get
      num          <- context.roundChanges.get.map(m => m.getOrElse(payload.view.round, MessageSet.empty).messages.size)
      _ <- if (roundState.waitingForRoundChange && num == validatorSet.f + 1) {

        /**
          * Whenever a validator receives F + 1 of ROUND CHANGE messages on the same proposed round number,
          * it compares the received one with its own. If the received is larger,
          * the validator broadcasts ROUND CHANGE message again with the received number.
          */
        if (roundState.round < payload.view.round) {
          Proxy.sendRoundChange(payload.view.round, context)
        } else {
          F.unit
        }
      } else if (num == 2 * validatorSet.f + 1 && (roundState.waitingForRoundChange || roundState.round < payload.view.round)) {

        /**
          * we've received 2f+1 ROUND CHANGE messages
          * 1.set current state to ROUND CHANGE
          * 2.manual trigger NewRoundAction to start a new round
          */
        context.setState(StateRoundChange) >>
          StateRoundChange.handle(NewRoundAction(payload.view.round), context)
      } else {
        F.unit
      }
    } yield ()
}

case object StateNewRound extends State {

  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      vals <- context.validatorSet.get
      _    <- if (vals.isProposer(context.address)) handleProposer(action, context) else handleValidator(action, context)
    } yield ()

  private def handlePreprepareAction[F[_]](message: IstanbulMessage, context: StateContext[F])(
      implicit F: Concurrent[F]): F[Unit] =
    for {
      current <- context.current.get
      _ = log.debug(s"handlePreprepareAction")
      preprepare  <- F.delay(RlpCodec.decode[Preprepare](message.msg.bits).require.value)
      checkResult <- checkProposal(message, context)
      _ <- checkResult match {
        case ProposalCheckResult.Success =>
          /**
            * I think this IF situation will never happen, don't know why Ethereum do this check
            */
          if (current.isLocked) {
            if (preprepare.block.header.hash == current.lockedHash) {

              /**
                * Case 2.1, received PRE-PREPARE on B: Broadcasts COMMIT on B
                * this situation may happened when some message delayed in the network,
                * and locked on the PRE-PREPARE message's block means we have received enough PREPARE or COMMIT messages,
                * so we can enter PREPARED state and broadcast COMMIT message directly,
                * because when receive enough PREPARE or COMMIT messages we will enter PREPARED at least
                *
                * article in github says it broadcasts a PREPARE message here, I think it's a mistake,
                * and code in Ethereum broadcasts a COMMIT message too.
                */
              context.setPreprepare(preprepare) >>
                context.setState(StatePrepared) >>
                Proxy.sendCommit(context)
            } else {

              /**
                * Case 2.2, received PRE-PREPARE on B': Broadcasts ROUND CHANGE.
                * LockedHash is not equal than the message's block hash, cause a ROUND CHANGE.
                */
              Proxy.sendNextRound(context)
            }
          } else {

            /**
              * we have no locked proposal
              * enter PREPREPARED and broadcast prepare
              */
            context.setPreprepare(preprepare) >>
              context.setState(StatePreprepared) >>
              Proxy.sendPrepare(context)
          }
        case ProposalCheckResult.FutureBlock =>
          // it's a future block,
          F.raiseError(new Exception("future block"))
        case ProposalCheckResult.UnauthorizedProposer =>
          F.raiseError(new Exception("unauthorized proposer"))
        case ProposalCheckResult.InvalidProposal =>
          // invalid proposal, broadcast ROUND CHANGE message along with the proposed round number
          Proxy.sendNextRound(context)
        case _ => F.raiseError(new Exception("check proposal exception"))
      }
    } yield ()

  private def handleProposer[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case ProposeAction(block) =>
        // proposer broadcast PRE-PREPARE message, then enters PREPREPARED state
        for {
          view <- context.view
          _ = log.debug(s"handleProposer")
          isProposer <- context.validatorSet.get.map(_.isProposer(context.address))
          _ <- if (block.header.number == view.blockNumber && isProposer) {
            for {
              msg     <- F.pure(Preprepare(view, block))
              payload <- msg.asBytes[F]
              _       <- Proxy.broadcast(IstanbulMessage.msgPreprepareCode, payload, context)
            } yield ()
          } else F.unit
        } yield ()
      case PreprepareAction(message)  => handlePreprepareAction(message, context)
      case RoundChangeAction(message) => handleRoundChange(message, context)
      case _                          => F.unit
    }

  /**
    * check the proposal is a valid block
    */
  private def checkProposal[F[_]](message: IstanbulMessage, context: StateContext[F])(
      implicit F: Concurrent[F]): F[ProposalCheckResult] =
    context.validatorSet.get.map(vs =>
      if (vs.isProposer(message.address)) {
        ProposalCheckResult.Success
      } else {
        ProposalCheckResult.UnauthorizedProposer
    })

  private def handleValidator[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case PreprepareAction(message)  => handlePreprepareAction(message, context)
      case RoundChangeAction(message) => handleRoundChange(message, context)
      case _                          => F.unit
    }
}

case object StatePreprepared extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case PrepareAction(message) =>
        for {
          check <- checkPrepare(message, context)
          _ <- if (check) {
            for {
              _     <- context.addPrepare(message)
              ready <- context.prepareReady
              _ <- if (ready) {

                /**
                  * 1.enter prepared when receive 2F+1 of valid PREPARE message
                  * 2.locked on current proposal
                  * 3.broadcast COMMIT message
                  */
                context.lockHash() >>
                  context.setState(StatePrepared) >>
                  Proxy.sendCommit(context)
              } else F.unit
            } yield ()
          } else F.unit
        } yield ()
      case CommitAction(message)      => handleCommitAction(message, context)
      case RoundChangeAction(message) => handleRoundChange(message, context)
      case _                          => F.unit
    }

}
case object StatePrepared extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case PrepareAction(message)     => acceptPrepare(message, context)
      case CommitAction(message)      => handleCommitAction(message, context)
      case RoundChangeAction(message) => handleRoundChange(message, context)
      case _                          => F.unit
    }
}
case object StateCommitted extends State {

  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case PrepareAction(message) => acceptPrepare(message, context)
      case CommitAction(message)  => acceptCommit(message, context)
      case InsertBlockAction =>
        for {

          /**
            * still need to call LockHash here since state can skip Prepared state and jump directly to the Committed state.
            */
          _        <- context.lockHash()
          proposal <- context.proposal
          _ <- proposal match {
            case Some(block) =>
              for {
                commits <- context.current.get.map(_.commits.messages.values.toList)
                committedSeals = commits.flatMap(_.committedSig)
                result <- insertBlock(block, committedSeals)
                _ <- if (!result) {

                  /**
                    * unlock block and broadcast ROUND CHANGE when insertion fails
                    */
                  context.setState(StateRoundChange) >>
                    context.unlockHash() >>
                    Proxy.sendNextRound(context)
                } else {

                  /**
                    * manual trigger NEW ROUND action
                    */
                  context.setState(StateFinalCommitted) >>
                    StateFinalCommitted.handle(NewRoundAction(0), context)
                }
              } yield ()
            case None => F.unit
          }
        } yield ()
      case RoundChangeAction(message) => handleRoundChange(message, context)
      case _                          => F.unit
    }
}

case object StateFinalCommitted extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case NewRoundAction(_) =>
        for {
          _       <- context.setState(StateNewRound)
          _       <- context.unlockHash()
          promise <- context.roundChangePromise.get
          _       <- promise.complete(0)
        } yield ()
      case _ => F.unit
    }
}

case object StateRoundChange extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    action match {
      case NewRoundAction(round) =>
        for {
          _       <- context.setState(StateNewRound)
          promise <- context.roundChangePromise.get
          _       <- promise.complete(round)
        } yield ()
      case RoundChangeAction(message) => handleRoundChange(message, context)
      case _                          => F.unit
    }
}
