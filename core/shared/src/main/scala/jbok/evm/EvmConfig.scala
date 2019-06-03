package jbok.evm

import jbok.common.math.N
import jbok.common.math.implicits._
import jbok.core.config.HistoryConfig
import jbok.core.models.UInt256
import jbok.evm.PrecompiledContracts.PrecompiledContract
import scodec.bits.ByteVector
import jbok.core.models.Address

object EvmConfig {

  type EvmConfigBuilder = Option[N] => EvmConfig

  val MaxCallDepth: Int = 1024

  /** used to artificially limit memory usage by incurring maximum gas cost */
  val MaxMemory: UInt256 = UInt256(Int.MaxValue)

  /**
    * returns the evm config that should be used for given block
    */
  def forBlock(blockNumber: N, historyConfig: HistoryConfig): EvmConfig = {
    val transitionBlockToConfigMapping: List[(N, EvmConfigBuilder)] = List(
      historyConfig.constantinopleBlockNumber   -> ConstantinopleConfigBuilder,
      historyConfig.byzantiumBlockNumber        -> ByzantiumConfigBuilder,
      historyConfig.spuriousDragonBlockNumber   -> SpuriousDragonConfigBuilder,
      historyConfig.tangerineWhistleBlockNumber -> TangerineWhistleConfigBuilder,
      historyConfig.homesteadBlockNumber        -> HomesteadConfigBuilder,
      historyConfig.frontierBlockNumber         -> FrontierConfigBuilder
    )

    // highest transition block that is less/equal to `blockNumber`
    val evmConfigBuilder = transitionBlockToConfigMapping
      .find(_._1 <= blockNumber)
      .map(_._2)
      .getOrElse(FrontierConfigBuilder)
    evmConfigBuilder(historyConfig.maxCodeSize)
  }

  val FrontierConfigBuilder: EvmConfigBuilder = maxCodeSize =>
    EvmConfig(
      feeSchedule = FeeSchedule.Frontier,
      opCodes = OpCodes.FrontierOpCodes,
      preCompiledContracts = PrecompiledContracts.FrontierContracts,
      subGasCapDivisor = None,
      chargeSelfDestructForNewAccount = false,
      maxCodeSize = maxCodeSize,
      exceptionalFailedCodeDeposit = false,
      traceInternalTransactions = false
    )

  val HomesteadConfigBuilder: EvmConfigBuilder = maxCodeSize =>
    FrontierConfigBuilder(maxCodeSize).copy(feeSchedule = FeeSchedule.Homestead, opCodes = OpCodes.HomesteadOpCodes, exceptionalFailedCodeDeposit = true)

  val TangerineWhistleConfigBuilder: EvmConfigBuilder = maxCodeSize =>
    HomesteadConfigBuilder(maxCodeSize).copy(feeSchedule = FeeSchedule.TangerineWhistle, subGasCapDivisor = Some(64), chargeSelfDestructForNewAccount = true)

  val SpuriousDragonConfigBuilder: EvmConfigBuilder = maxCodeSize =>
    TangerineWhistleConfigBuilder(maxCodeSize).copy(feeSchedule = FeeSchedule.SpuriousDragon, noEmptyAccounts = true)

  val ByzantiumConfigBuilder: EvmConfigBuilder = maxCodeSize =>
    SpuriousDragonConfigBuilder(maxCodeSize)
      .copy(opCodes = OpCodes.ByzantiumOpCodes, preCompiledContracts = PrecompiledContracts.ByzantiumContracts)

  val ConstantinopleConfigBuilder: EvmConfigBuilder = maxCodeSize =>
    ByzantiumConfigBuilder(maxCodeSize)
      .copy(opCodes = OpCodes.ConstantinopleOpCodes, feeSchedule = FeeSchedule.Constantinople, sstoreGasMetering = true)

}

final case class EvmConfig(
    feeSchedule: FeeSchedule,
    opCodes: List[OpCode],
    preCompiledContracts: Map[Address, PrecompiledContract],
    subGasCapDivisor: Option[Long],
    chargeSelfDestructForNewAccount: Boolean,
    maxCodeSize: Option[N],
    traceInternalTransactions: Boolean,
    exceptionalFailedCodeDeposit: Boolean,
    noEmptyAccounts: Boolean = false,
    sstoreGasMetering: Boolean = false
) {

  import feeSchedule._
  import EvmConfig._

  val byteToOpCode: Map[Byte, OpCode] =
    opCodes.map(op => op.code.toByte -> op).toMap

  /**
    * Calculate gas cost of memory usage. Incur a blocking gas cost if memory usage exceeds reasonable limits.
    *
    * @param memSize  current memory size in bytes
    * @param offset   memory offset to be written/read
    * @param dataSize size of data to be written/read in bytes
    * @return gas cost
    */
  def calcMemCost(memSize: N, offset: N, dataSize: N): N = {

    /** See YP H.1 (222) */
    def c(m: N): N = {
      val a = wordsForBytes(m)
      G_memory * a + a * a / 512
    }

    val memNeeded = if (dataSize == 0) N(0) else offset + dataSize
    if (memNeeded > MaxMemory)
      UInt256.MaxValue / 2
    else if (memNeeded <= memSize)
      0
    else
      c(memNeeded) - c(memSize)
  }

  /**
    * Calculates transaction intrinsic gas. See YP section 6.2
    *
    */
  def calcTransactionIntrinsicGas(txData: ByteVector, isContractCreation: Boolean): N = {
    val txDataZero    = txData.foldLeft(0)((c, d) => if (d == 0) c + 1 else c)
    val txDataNonZero = txData.length - txDataZero

    txDataZero * G_txdatazero +
      txDataNonZero * G_txdatanonzero +
      (if (isContractCreation) G_txcreate else N(0)) +
      G_transaction
  }

  /**
    * If the initialization code completes successfully, a final contract-creation cost is paid, the code-deposit cost,
    * proportional to the size of the created contract’s code. See YP equation (96)
    *
    * @param executionResultData Transaction code initialization result
    * @return Calculated gas cost
    */
  def calcCodeDepositCost(executionResultData: ByteVector): N =
    G_codedeposit * executionResultData.size

  /**
    * a helper method used for gas adjustment in CALL and CREATE opcode, see YP eq. (224)
    */
  def gasCap(g: N): N =
    subGasCapDivisor.map(d => g - g / d).getOrElse(g)
}

object FeeSchedule {
  trait FrontierFeeSchedule extends FeeSchedule {
    override val G_zero: N          = 0
    override val G_base: N          = 2
    override val G_verylow: N       = 3
    override val G_low: N           = 5
    override val G_mid: N           = 8
    override val G_high: N          = 10
    override val G_balance: N       = 20
    override val G_sload: N         = 50
    override val G_jumpdest: N      = 1
    override val G_sset: N          = 20000
    override val G_sreset: N        = 5000
    override val R_sclear: N        = 15000
    override val G_snoop: N         = 0
    override val G_sfresh: N        = 0
    override val G_sdirty: N        = 0
    override val R_sresetclear: N   = 0
    override val R_sreset: N        = 0
    override val R_selfdestruct: N  = 24000
    override val G_selfdestruct: N  = 0
    override val G_create: N        = 32000
    override val G_codedeposit: N   = 200
    override val G_call: N          = 40
    override val G_callvalue: N     = 9000
    override val G_callstipend: N   = 2300
    override val G_newaccount: N    = 25000
    override val G_exp: N           = 10
    override val G_expbyte: N       = 10
    override val G_memory: N        = 3
    override val G_txcreate: N      = 0
    override val G_txdatazero: N    = 4
    override val G_txdatanonzero: N = 68
    override val G_transaction: N   = 21000
    override val G_log: N           = 375
    override val G_logdata: N       = 8
    override val G_logtopic: N      = 375
    override val G_sha3: N          = 30
    override val G_sha3word: N      = 6
    override val G_copy: N          = 3
    override val G_blockhash: N     = 20
    override val G_extcodesize: N   = 20
    override val G_extcodecopy: N   = 20
    override val G_extcodehash: N   = 0
  }

  object Frontier extends FrontierFeeSchedule

  trait HomesteadFeeSchedule extends FrontierFeeSchedule {
    override val G_txcreate: N = 32000
  }

  object Homestead extends HomesteadFeeSchedule

  trait TangerineWhistleFeeSchedule extends HomesteadFeeSchedule {
    override val G_sload: N        = 200
    override val G_call: N         = 700
    override val G_balance: N      = 400
    override val G_selfdestruct: N = 5000
    override val G_extcodesize: N  = 700
    override val G_extcodecopy: N  = 700
  }

  object TangerineWhistle extends TangerineWhistleFeeSchedule

  trait SpuriousDragonFeeSchedule extends TangerineWhistleFeeSchedule {
    override val G_expbyte: N = 50
  }

  object SpuriousDragon extends SpuriousDragonFeeSchedule

  trait ConstantinopleFeeSchedule extends SpuriousDragonFeeSchedule {
    override val G_extcodehash: N = 400
    override val G_snoop: N       = 200
    override val G_sfresh: N      = 5000
    override val G_sdirty: N      = 200
    override val R_sresetclear: N = 19800
    override val R_sreset: N      = 4800
  }

  object Constantinople extends ConstantinopleFeeSchedule
}

trait FeeSchedule {
  val G_zero: N
  val G_base: N
  val G_verylow: N
  val G_low: N
  val G_mid: N
  val G_high: N
  val G_balance: N
  val G_sload: N
  val G_jumpdest: N
  val G_sset: N
  val G_sreset: N
  val R_sclear: N
  val G_snoop: N
  val G_sfresh: N
  val G_sdirty: N
  val R_sresetclear: N
  val R_sreset: N
  val R_selfdestruct: N
  val G_selfdestruct: N
  val G_create: N
  val G_codedeposit: N
  val G_call: N
  val G_callvalue: N
  val G_callstipend: N
  val G_newaccount: N
  val G_exp: N
  val G_expbyte: N
  val G_memory: N
  val G_txcreate: N
  val G_txdatazero: N
  val G_txdatanonzero: N
  val G_transaction: N
  val G_log: N
  val G_logdata: N
  val G_logtopic: N
  val G_sha3: N
  val G_sha3word: N
  val G_copy: N
  val G_blockhash: N
  val G_extcodesize: N
  val G_extcodecopy: N
  val G_extcodehash: N
}
