package jbok.core.consensus.istanbul

import jbok.core.messages.IstanbulMessage
import jbok.core.models.Block

trait Action

/**
  * ProposeAction represent a action proposed by proposer，
  * this action will trigger the proposer broadcast a PRE-PREPARE message to validators
  * and entr PRE-PREPARED state
  */
case class ProposeAction(block: Block) extends Action

/**
  * PreprepareAction means receive a PRE-PREPARE message from proposer
  * message payload is the bytes of Preprepare
  */
case class PreprepareAction(message: IstanbulMessage) extends Action

case class PrepareAction(message: IstanbulMessage) extends Action

case class CommitAction(message: IstanbulMessage) extends Action

case class RoundChangeAction(message: IstanbulMessage) extends Action

/**
  * NewRoundAction means need to start a new round
  * when a block insertion succeed, or receive 2F+1 ROUND CHANGE message, we will trigger a NewRoundAction
  * and then enter StateNewRound
  */
case class NewRoundAction(round:Int) extends Action

case object InsertBlockAction extends Action

case object TimeoutAction extends Action
