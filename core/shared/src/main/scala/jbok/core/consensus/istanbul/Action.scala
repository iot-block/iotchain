package jbok.core.consensus.istanbul

import jbok.core.messages.IstanbulMessage
import jbok.core.models.Block

sealed trait Action

/**
  * ProposeAction represent a action proposed by proposer，
  * this action will trigger the proposer broadcast a PRE-PREPARE message to validators
  * and enter PRE-PREPARED state
  */
final case class ProposeAction(block: Block) extends Action

/**
  * PreprepareAction means receive a PRE-PREPARE message from proposer
  * message payload is the bytes of Preprepare
  */
final case class PreprepareAction(message: IstanbulMessage) extends Action

final case class PrepareAction(message: IstanbulMessage) extends Action

final case class CommitAction(message: IstanbulMessage) extends Action

final case class RoundChangeAction(message: IstanbulMessage) extends Action

/**
  * NewRoundAction means need to start a new round
  * when a block insertion succeed, or receive 2F+1 ROUND CHANGE message, we will trigger a NewRoundAction
  * and then enter StateNewRound
  */
final case class NewRoundAction(round: Int) extends Action

case object InsertBlockAction extends Action

case object TimeoutAction extends Action
