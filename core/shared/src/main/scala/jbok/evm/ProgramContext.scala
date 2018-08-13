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

    // YP eq (91)
    val inputData =
      if (stx.isContractInit) ByteVector.empty
      else stx.payload

    val senderAddress = stx.senderAddress(None).getOrElse(Address.empty)

    val env = ExecEnv(
      recipientAddress,
      senderAddress,
      senderAddress,
      UInt256(stx.gasPrice),
      inputData,
      UInt256(stx.value),
      program,
      blockHeader,
      callDepth = 0
    )

    val gasLimit = stx.gasLimit - config.calcTransactionIntrinsicGas(stx.payload, stx.isContractInit)

    ProgramContext[F](env, recipientAddress, gasLimit, world, config)
  }

//  private def getSenderAddress(stx: SignedTransaction, number: BigInt): Address = {
//    val addrOpt =
//      if (number >= blockChainConfig.eip155BlockNumber)
//        stx.senderAddress(Some(blockChainConfig.chainId))
//      else
//        stx.senderAddress(None)
//    addrOpt.getOrElse(Address.empty)
//  }
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
