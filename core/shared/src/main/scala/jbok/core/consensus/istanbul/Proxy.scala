package jbok.core.consensus.istanbul

import cats.effect.Concurrent
import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import cats.implicits._
import jbok.core.messages.IstanbulMessage

object Proxy {
  private val log = org.log4s.getLogger("Proxy")

  def finalizeMessage[F[_]](msgCode: Int, payload: ByteVector, context: StateContext[F])(
      implicit F: Concurrent[F]): F[IstanbulMessage] =
    for {
      proposal <- context.proposal.flatMap(opt => F.fromOption(opt, new Exception("unexpected None")))
      msgForSign <- msgCode match {
        case IstanbulMessage.msgCommitCode =>
          for {
            seal <- F.pure(proposal.header.hash ++ ByteVector(IstanbulMessage.msgCommitCode))
            sig  <- context.signFunc(seal)
          } yield IstanbulMessage(msgCode, payload, context.address, ByteVector.empty, Some(sig))
        case _ => F.pure(IstanbulMessage(msgCode, payload, context.address, ByteVector.empty, None))
      }
      rlp  <- msgForSign.asBytes[F]
      sign <- context.signFunc(rlp)
    } yield msgForSign.copy(signature = ByteVector(sign.bytes))

  def broadcast[F[_]](msgCode: Int, payload: ByteVector, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      message <- finalizeMessage(msgCode, payload, context)
      _ = log.debug(s"broadcast $message")
      _ <- context.eventHandler(message) //send to self
      // TODO: send to others
    } yield ()

  def sendPrepare[F[_]](context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    sendSubjectMessage(IstanbulMessage.msgPrepareCode, context)

  /**
    * send ROUND CHANGE message with current round+1
    */
  def sendNextRound[F[_]](context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      roundState <- context.current.get
      _          <- sendRoundChange(roundState.round + 1, context)
    } yield ()

  def sendRoundChange[F[_]](round: Int, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      rs <- context.current.get
      // compare current round with new round, if current is larger, we will not send the ROUND CHANGE message
      _ <- if (rs.round >= round) {
        F.unit
      } else {
        // update current round
        context.updateCurrentRound(round) >>
          // broadcast ROUND CHANGE message
          broadcast(IstanbulMessage.msgRoundChange,
                    Subject(View(round, rs.blockNumber), ByteVector.empty).asValidBytes,
                    context)
      }
    } yield ()

  def sendCommit[F[_]](context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    sendSubjectMessage(IstanbulMessage.msgCommitCode, context)

  /**
    * simple send current subject message
    */
  private def sendSubjectMessage[F[_]](msgCode: Int, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      cs <- context.currentSubject()
      _ <- cs match {
        case Some(subject) => broadcast(msgCode, subject.asValidBytes, context)
        case None          => F.unit
      }
    } yield ()
}
