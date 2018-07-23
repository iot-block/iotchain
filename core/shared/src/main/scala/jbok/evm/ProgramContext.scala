package jbok.evm

import cats.effect.Sync
import jbok.core.models.{Address, BlockHeader, SignedTransaction, UInt256}
import scodec.bits.ByteVector

object ProgramContext {
  def apply[F[_]: Sync](
      stx: SignedTransaction,
      recipientAddress: Address,
      program: Program,
      blockHeader: BlockHeader,
      world: WorldStateProxy[F],
      config: EvmConfig
  ): ProgramContext[F] = {

    import stx.tx

    // YP eq (91)
    val inputData =
      if (tx.isContractInit) ByteVector.empty
      else tx.payload

    val senderAddress = stx.senderAddress

    val env = ExecEnv(
      recipientAddress,
      senderAddress,
      senderAddress,
      UInt256(tx.gasPrice),
      inputData,
      UInt256(tx.value),
      program,
      blockHeader,
      callDepth = 0
    )

    val gasLimit = tx.gasLimit - config.calcTransactionIntrinsicGas(tx.payload, tx.isContractInit)

    ProgramContext[F](env, recipientAddress, gasLimit, world, config)
  }
}

/**
  * Input parameters to a program executed on the EVM. Apart from the code itself
  * it should have all (interfaces to) the data accessible from the EVM.
  *
  * @param env                      set of constants for the execution
  * @param receivingAddr            used for determining whether a precompiled contract is being called (potentially
  *                                 different from the addresses defined in env)
  * @param startGas                 initial gas for the execution
  * @param world                    provides interactions with world state
  * @param config                   evm config
  * @param initialAddressesToDelete contains initial set of addresses to delete (from lower depth calls)
  */
case class ProgramContext[F[_]: Sync](
    env: ExecEnv,
    receivingAddr: Address,
    startGas: BigInt,
    world: WorldStateProxy[F],
    config: EvmConfig,
    initialAddressesToDelete: Set[Address] = Set.empty
)
