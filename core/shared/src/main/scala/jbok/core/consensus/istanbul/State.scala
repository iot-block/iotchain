package jbok.core.consensus.istanbul

import cats.effect.Sync
import jbok.codec.rlp.RlpCodec
import jbok.core.consensus.istanbul.State._
import jbok.codec.rlp.implicits._
import cats.implicits._
import jbok.core.models.{Address, Block}
import scodec.bits.ByteVector

import scala.collection.mutable.{Map => MMap}

trait State {
  def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit]

  /**
    * check the message payload subject is equal than current subject
    */
  private def checkSubject[F[_]](message: Message, context: StateContext[F])(implicit F: Sync[F]): F[Boolean] =
    for {
      subject <- context.currentSubject()
      result <- subject match {
        case Some(s) =>
          F.delay(RlpCodec.decode[Subject](message.msg.bits).require.value)
            .map(
              prepare =>
                prepare.view.sequence == s.view.sequence
                  && prepare.view.round == s.view.round
                  && prepare.digest == s.digest)
        case None => F.pure(false)
      }
    } yield result

  def checkPrepare[F[_]](message: Message, context: StateContext[F])(implicit F: Sync[F]): F[Boolean] =
    checkSubject(message, context)

  def checkCommit[F[_]](message: Message, context: StateContext[F])(implicit F: Sync[F]): F[Boolean] =
    checkSubject(message, context)

//  TODO: insert block
  def insertBlock[F[_]](block: Block, committedSeals: List[ByteVector])(implicit F: Sync[F]): F[Boolean] =
    F.pure(true)

  def handleCommitAction[F[_]](message: Message, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
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

}
object State {

//  TODO: do sign
  private def sign(bytes: ByteVector): ByteVector = bytes

  private def finalizeMessage[F[_]](msgCode: Int, payload: ByteVector, context: StateContext[F])(
      implicit F: Sync[F]): F[Message] =
    for {
      proposal <- context.proposal
      msgForSign = msgCode match {
        case Message.msgCommitCode => {
          val seal          = proposal.get.header.hash ++ ByteVector(Message.msgCommitCode)
          val committedSeal = sign(seal)
          Message(msgCode, payload, context.address, ByteVector.empty, committedSeal)
        }
        case _ => Message(msgCode, payload, context.address, ByteVector.empty, ByteVector.empty)
      }
      signature <- F.delay(RlpCodec.encode(msgForSign).require.bytes)

    } yield msgForSign.copy(signature = signature)

  def broadcast[F[_]](msgCode: Int, payload: ByteVector, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    for {
      message <- finalizeMessage(msgCode, payload, context)
//      TODO: broadcast
    } yield ()

  def sendPrepare[F[_]](context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    sendSubjectMessage(Message.msgPrepareCode, context)

  def startNewRound[F[_]](round: BigInt, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    context.newRoundFunc(round, context)

  /**
    * send ROUND CHANGE message with current round+1
    */
  def sendNextRound[F[_]](context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    for {
      roundState <- context.current.get
      _          <- sendRoundChange(roundState.round + 1, context)
    } yield ()

  def sendRoundChange[F[_]](round: BigInt, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    for {
      rs <- context.current.get
//      compare current round with new round, if current is larger, we will not send the ROUND CHANGE message
      _ <- if (rs.round >= round) {
        F.unit
      } else {
//        update current round
        context.updateCurrentRound(round) >>
//        broadcast ROUND CHANGE message
          broadcast(Message.msgRoundChange,
                    RlpCodec.encode(Subject(View(round, rs.sequence), ByteVector.empty)).require.bytes,
                    context)
      }
    } yield ()

  def sendCommit[F[_]](context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    sendSubjectMessage(Message.msgCommitCode, context)

  /**
    * simple send current subject message
    */
  private def sendSubjectMessage[F[_]](msgCode: Int, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    for {
      cs <- context.currentSubject()
      _ <- cs match {
        case Some(subject) => broadcast(msgCode, RlpCodec.encode(subject).require.bytes, context)
        case None          => F.unit
      }
    } yield ()

}

case object StateNewRound extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    context.validatorSet.get.map(_.isProposer(context.address) match {
      case true => handleProposer(action, context)
      case _    => handleValidator(action, context)
    })

//  proposer broadcast PRE-PREPARE message, then enters PREPREPARED state
  private def handleProposer[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
      case ProposeAction(block) => {
        for {
          view       <- context.view
          isProposer <- context.validatorSet.get.map(_.isProposer(context.address))
          _ <- if (block.header.number == view.sequence && isProposer) {
            for {
              msg     <- F.pure(Preprepare(view, block))
              payload <- F.delay(RlpCodec.encode(msg).require.bytes)
              _       <- broadcast(Message.msgPreprepareCode, payload, context)
              _       <- context.setPreprepare(msg)
              _       <- context.setState(StatePreprepared)
            } yield ()
          } else F.unit
        } yield ()
      }
      case _ => F.unit
    }

//  check the proposal is a valid block
  private def checkProposal[F[_]](message: Message, context: StateContext[F])(
      implicit F: Sync[F]): F[ProposalCheckResult] =
    context.validatorSet.get.map(vs =>
      if (vs.isProposer(message.address)) {
        ProposalCheckResult.Success
      } else {
        ProposalCheckResult.UnauthorizedProposer
    })

//  validator broadcast PREPARE message and enter PREPREPARED state upon receiving PRE-PREPARE message
  private def handleValidator[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
      case PreprepareAction(message) => {
        for {
          current     <- context.current.get
          preprepare  <- F.delay(RlpCodec.decode[Preprepare](message.msg.bits).require.value)
          checkResult <- checkProposal(message, context)
          _ <- checkResult match {
            case ProposalCheckResult.Success => {

              /**
                * I think this IF situation will never happen, don't know why Ethereum do this check
                */
              if (current.isLocked()) {
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
                    sendCommit(context)
                } else {

                  /**
                    * Case 2.2, received PRE-PREPARE on B': Broadcasts ROUND CHANGE.
                    * LockedHash is not equal than the message's block hash, cause a ROUND CHANGE.
                    */
                  sendNextRound(context)
                }
              } else {

                /**
                  * we have no locked proposal
                  * enter PREPREPARED and broadcast prepare
                  */
                sendPrepare(context) >>
                  context.setPreprepare(preprepare) >>
                  context.setState(StatePreprepared)
              }
            }
            case ProposalCheckResult.FutureBlock => {
//              it's a future block,
              F.raiseError(new Exception("future block"))
            }
            case ProposalCheckResult.UnauthorizedProposer => {
              F.raiseError(new Exception("unauthorized proposer"))
            }
            case ProposalCheckResult.InvalidProposal => {
//              invalid proposal, broadcast ROUND CHANGE message along with the proposed round number
              sendNextRound(context)
            }
            case _ => F.raiseError(new Exception("check proposal exception"))
          }

        } yield ()
      }
      case _ => F.unit
    }
}

case object StatePreprepared extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
      case PrepareAction(message) => {
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
                  sendCommit(context)
              } else F.unit
            } yield ()
          } else F.unit
        } yield ()
      }
      case CommitAction(message) => handleCommitAction(message, context)
      case RoundChangeAction(message) =>
        for {
          payload <- F.delay(RlpCodec.decode[Subject](message.msg.bits).require.value)
          _ <- context.roundChanges.update(m => {
            m.getOrElse(payload.view.round, MessageSet.empty).addMessage(message)
            m
          })
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
              sendRoundChange(payload.view.round, context)
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
      case _ => F.unit
    }

}
case object StatePrepared extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
      case CommitAction(message) => handleCommitAction(message, context)
      case _                     => F.unit
    }
}
case object StateCommitted extends State {

  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
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
                committedSeals = commits.map(_.committedSeal)
                result <- insertBlock(block, committedSeals)
                _ <- if (!result) {

                  /**
                    * unlock block and broadcast ROUND CHANGE when insertion fails
                    */
                  context.unlockHash() >>
                    sendNextRound(context)
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
      case _ => F.unit
    }
}

case object StateFinalCommitted extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
      case NewRoundAction(round) =>
        for {
          _ <- context.setState(StateNewRound)
          _ <- startNewRound(0, context)
        } yield ()
      case _ => F.unit
    }
}

case object StateRoundChange extends State {
  override def handle[F[_]](action: Action, context: StateContext[F])(implicit F: Sync[F]): F[Unit] =
    action match {
      case NewRoundAction(round) =>
        for {
          _ <- context.setState(StateNewRound)
          _ <- startNewRound(round, context)
        } yield ()
      case _ => F.unit
    }
}
