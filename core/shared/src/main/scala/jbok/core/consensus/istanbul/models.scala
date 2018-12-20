package jbok.core.consensus.istanbul

import cats.effect.{Concurrent, ConcurrentEffect, IO, Sync}
import cats.effect.concurrent.{Deferred, Ref}
import jbok.core.models.{Address, Block}
import scodec.bits.ByteVector
import cats.implicits._
import io.circe.generic.JsonCodec
import jbok.core.messages.{IstanbulMessage, Message}
import jbok.crypto.signature.{CryptoSignature, KeyPair}

import scala.collection.Iterable
import scala.collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}

case class IstanbulExtra(
    validators: List[Address],
    seal: ByteVector,
    committedSeals: List[ByteVector]
)

sealed trait CheckResult
object CheckResult {
  case object UnauthorizedAddress extends CheckResult
  case object InvalidMessage      extends CheckResult
  case object FutureMessage       extends CheckResult
  case object OldMessage          extends CheckResult
  case object Success             extends CheckResult
}

sealed trait ProposalCheckResult
object ProposalCheckResult {
  case object UnauthorizedProposer extends ProposalCheckResult
  case object InvalidProposal      extends ProposalCheckResult
  case object FutureBlock          extends ProposalCheckResult
  case object Success              extends ProposalCheckResult
}

//case class IstanbulMessage(
//  msgCode: Int,
//  msg: ByteVector = ByteVector.empty,
//  address: Address = Address.empty,
//  signature: ByteVector = ByteVector.empty,
//  ommittedSeal: ByteVector = ByteVector.empty
//) extends Message(0x5000)

//object IstanbulMessage {
//  val msgPreprepareCode = 0
//  val msgPrepareCode    = 1
//  val msgCommitCode     = 2
//  val msgRoundChange    = 3
//  val msgAll            = 4
//}

case class Preprepare(view: View, block: Block)

/**
  *
  * @param view
  * @param digest proposal hash
  */
case class Subject(view: View, digest: ByteVector)

//context for state transition
case class StateContext[F[_]](
    keyPair: KeyPair,
    validatorSet: Ref[F, ValidatorSet],
    current: Ref[F, RoundState],
    state: Ref[F, State],
    roundChanges: Ref[F, Map[BigInt, MessageSet]],
    roundChangePromise: Ref[F, Deferred[F, BigInt]],
    signFunc: ByteVector => F[CryptoSignature],
    eventHandler: IstanbulMessage => F[Unit]
)(implicit F: Concurrent[F]) {
  def address: Address = Address(keyPair)

  def lockHash(): F[Unit] =
    current.update(s => {
      s.preprepare match {
        case Some(p) => s.copy(lockedHash = p.block.header.hash)
        case None    => s
      }
    })
  def unlockHash(): F[Unit] = current.update(_.copy(lockedHash = ByteVector.empty))

  def prepareReady: F[Boolean] =
    for {
      rs <- current.get
      vs <- validatorSet.get
      enough = rs.prepares.messages.size > 2 * vs.f
    } yield enough

  def commitReady: F[Boolean] =
    for {
      rs <- current.get
      vs <- validatorSet.get
      enough = rs.commits.messages.size > 2 * vs.f
    } yield enough

  def view: F[View]               = current.get.map(s => View(s.round, s.sequence))
  def proposal: F[Option[Block]]  = current.get.map(_.preprepare.map(p => p.block))
  def setState(s: State): F[Unit] = state.set(s)

  def addPrepare(message: IstanbulMessage): F[Unit] =
    current.update(rs => rs.copy(prepares = rs.prepares.addMessage(message)))

  def addCommit(message: IstanbulMessage): F[Unit] =
    current.update(rs => rs.copy(commits = rs.commits.addMessage(message)))

  def addRoundChange(round: BigInt, message: IstanbulMessage): F[Unit] =
    roundChanges.update(m => m + (round -> (m.getOrElse(round, MessageSet.empty).addMessage(message))))
//    roundChanges.update(messages => {
//      messages.getOrElseUpdate(round, MessageSet.empty).addMessage(message)
//      messages
//    })

  /**
    * delete the messages with smaller round
    */
  private def clearRoundChange(round: BigInt): F[Unit] =
    roundChanges.update(_.filter(_._1 >= round))

  /**
    *
    * update current round number and keep previous sequence number
    * with no checking the given round number is valid or not, so you must check it by yourself.
    *
    * if is Locked, only set round number to RoundState,
    * else set round number and clear preprepare
    *
    * locked means we can only propose this block, so we don't clear preprepare when it's locked
    * and otherwise we clear preprepare when it's not locked since we fire a NEW ROUND when this
    * function is called
    */
  def updateCurrentRound(round: BigInt): F[Unit] =
    for {
      _ <- current.update(s => {
        if (s.isLocked)
          s.copy(round = round, waitingForRoundChange = true, prepares = MessageSet.empty, commits = MessageSet.empty)
        else
          s.copy(round = round,
                 preprepare = None,
                 waitingForRoundChange = true,
                 prepares = MessageSet.empty,
                 commits = MessageSet.empty)
      })
      _ <- clearRoundChange(round)
    } yield ()

  def setPreprepare(preprepare: Preprepare): F[Unit] = current.update(_.copy(preprepare = Some(preprepare)))

  def currentSubject(): F[Option[Subject]] =
    for {
      c <- current.get
      subject = c.preprepare match {
        case Some(p) => {
          val view = View(c.round, c.sequence)
          Some(Subject(view, p.block.header.hash))
        }
        case None => None
      }
    } yield subject
}

case class View(
    val round: BigInt,
    val sequence: BigInt
)
object View {
  def empty: View = View(0, 0)
}

@JsonCodec
case class ValidatorSet(
    proposer: Address,
    validators: ArrayBuffer[Address]
) {

  def isProposer(address: Address): Boolean = contains(address) && proposer == address

  /**
    * f represent the constant F in Istanbul BFT defined
    */
  def f = Math.ceil(validators.size / 3.0).toInt - 1

  def contains(address: Address): Boolean = validators.contains(address)

  def addValidator(address: Address): Unit = validators += address

  def removeValidator(address: Address): Unit = validators -= address

  def calculateProposer(lastProposer: Address, round: BigInt, policy: Int): Option[Address] =
    policy match {
      case IstanbulConfig.roundRobin => roundRobinProposer(lastProposer, round)
      case IstanbulConfig.sticky     => stickyProposer(lastProposer, round)
      case _                         => None
    }

  private def roundRobinProposer(lastProposer: Address, round: BigInt): Option[Address] = {
    if (validators.isEmpty) return None

    val seed =
      if (proposer == Address.empty || !validators.contains(lastProposer)) round
      else validators.toList.indexOf(lastProposer) + round

    val robin = seed % validators.size
    Option(validators(robin.intValue()))
  }

  private def stickyProposer(lastProposer: Address, round: BigInt): Option[Address] = None

}
object ValidatorSet {
  def empty: ValidatorSet = ValidatorSet(proposer = Address.empty, validators = ArrayBuffer.empty)

  def apply(
      proposer: Address,
      validators: ArrayBuffer[Address]
  ): ValidatorSet = new ValidatorSet(proposer, validators)

  def apply(
      validators: Iterable[Address]
  ): ValidatorSet = ValidatorSet(validators.head, ArrayBuffer.empty ++ validators)
}

case class MessageSet(
    view: View,
    messages: MMap[Address, IstanbulMessage]
) {
  def addMessage(message: IstanbulMessage): MessageSet = {
    messages.put(message.address, message)
    this
  }
}
object MessageSet {
  def empty: MessageSet = MessageSet(View.empty, MMap.empty)
}

case class RoundState(
    round: BigInt,
    sequence: BigInt,
    preprepare: Option[Preprepare],
    prepares: MessageSet,
    commits: MessageSet,
    lockedHash: ByteVector,
    waitingForRoundChange: Boolean = false
) {
  def isLocked: Boolean = lockedHash != ByteVector.empty
}

case class RoundChangeSet(
    val validatorSet: ValidatorSet,
    // round -> messages
    val roundChanges: MMap[BigInt, MessageSet]
)
