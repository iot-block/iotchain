package jbok.core.consensus.istanbul

import cats.effect.Concurrent
import jbok.codec.rlp.RlpCodec
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import cats.implicits._


object Proxy {
  // TODO: do sign
  private def sign(bytes: ByteVector): ByteVector = bytes

  private def finalizeMessage[F[_]](msgCode: Int, payload: ByteVector, context: StateContext[F])(
      implicit F: Concurrent[F]): F[Message] =
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

  def broadcast[F[_]](msgCode: Int, payload: ByteVector, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      message <- finalizeMessage(msgCode, payload, context)
      // TODO: broadcast
    } yield ()

  def sendPrepare[F[_]](context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    sendSubjectMessage(Message.msgPrepareCode, context)

  /**
    * send ROUND CHANGE message with current round+1
    */
  def sendNextRound[F[_]](context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      roundState <- context.current.get
      _          <- sendRoundChange(roundState.round + 1, context)
    } yield ()

  def sendRoundChange[F[_]](round: BigInt, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      rs <- context.current.get
      // compare current round with new round, if current is larger, we will not send the ROUND CHANGE message
      _ <- if (rs.round >= round) {
        F.unit
      } else {
        // update current round
        context.updateCurrentRound(round) >>
          // broadcast ROUND CHANGE message
          broadcast(Message.msgRoundChange,
                    RlpCodec.encode(Subject(View(round, rs.sequence), ByteVector.empty)).require.bytes,
                    context)
      }
    } yield ()

  def sendCommit[F[_]](context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    sendSubjectMessage(Message.msgCommitCode, context)

  /**
    * simple send current subject message
    */
  private def sendSubjectMessage[F[_]](msgCode: Int, context: StateContext[F])(implicit F: Concurrent[F]): F[Unit] =
    for {
      cs <- context.currentSubject()
      _ <- cs match {
        case Some(subject) => broadcast(msgCode, RlpCodec.encode(subject).require.bytes, context)
        case None          => F.unit
      }
    } yield ()
}
