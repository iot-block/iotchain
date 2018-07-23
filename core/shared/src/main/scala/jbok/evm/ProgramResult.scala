package jbok.evm

import jbok.core.models.{Address, TxLogEntry}
import scodec.bits.ByteVector

/**
  * Representation of the result of execution of a contract
  *
  * @param returnData        bytes returned by the executed contract (set by [[RETURN]] opcode)
  * @param gasRemaining      amount of gas remaining after execution
  * @param world             represents changes to the world state
  * @param addressesToDelete list of addresses of accounts scheduled to be deleted
  * @param internalTxs       list of internal transactions (for debugging/tracing) if enabled in config
  * @param error             defined when the program terminated abnormally
  */
case class ProgramResult[F[_]](
    returnData: ByteVector,
    gasRemaining: BigInt,
    world: WorldStateProxy[F],
    addressesToDelete: Set[Address],
    logs: List[TxLogEntry],
    internalTxs: List[InternalTransaction],
    gasRefund: BigInt,
    error: Option[ProgramError]
)
