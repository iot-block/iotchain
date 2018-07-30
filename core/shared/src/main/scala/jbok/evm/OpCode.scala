package jbok.evm

import cats.implicits._
import cats.effect.Sync
import jbok.core.models._
import scodec.bits.ByteVector
import jbok.crypto._
import jbok.common._
import UInt256._
import cats.data.OptionT

object OpCodes {

  val LogOpCodes: List[OpCode] = List(LOG0, LOG1, LOG2, LOG3, LOG4)

  val SwapOpCodes: List[OpCode] = List(
    SWAP1,
    SWAP2,
    SWAP3,
    SWAP4,
    SWAP5,
    SWAP6,
    SWAP7,
    SWAP8,
    SWAP9,
    SWAP10,
    SWAP11,
    SWAP12,
    SWAP13,
    SWAP14,
    SWAP15,
    SWAP16
  )

  val DupOpCodes: List[OpCode] =
    List(DUP1, DUP2, DUP3, DUP4, DUP5, DUP6, DUP7, DUP8, DUP9, DUP10, DUP11, DUP12, DUP13, DUP14, DUP15, DUP16)

  val PushOpCodes: List[OpCode] = List(
    PUSH1,
    PUSH2,
    PUSH3,
    PUSH4,
    PUSH5,
    PUSH6,
    PUSH7,
    PUSH8,
    PUSH9,
    PUSH10,
    PUSH11,
    PUSH12,
    PUSH13,
    PUSH14,
    PUSH15,
    PUSH16,
    PUSH17,
    PUSH18,
    PUSH19,
    PUSH20,
    PUSH21,
    PUSH22,
    PUSH23,
    PUSH24,
    PUSH25,
    PUSH26,
    PUSH27,
    PUSH28,
    PUSH29,
    PUSH30,
    PUSH31,
    PUSH32
  )

  val FrontierOpCodes: List[OpCode] =
    LogOpCodes ++ SwapOpCodes ++ PushOpCodes ++ DupOpCodes ++ List(
      STOP,
      ADD,
      MUL,
      SUB,
      DIV,
      SDIV,
      MOD,
      SMOD,
      ADDMOD,
      MULMOD,
      EXP,
      SIGNEXTEND,
      LT,
      GT,
      SLT,
      SGT,
      EQ,
      ISZERO,
      AND,
      OR,
      XOR,
      NOT,
      BYTE,
      SHA3,
      ADDRESS,
      BALANCE,
      ORIGIN,
      CALLER,
      CALLVALUE,
      CALLDATALOAD,
      CALLDATASIZE,
      CALLDATACOPY,
      CODESIZE,
      CODECOPY,
      GASPRICE,
      EXTCODESIZE,
      EXTCODECOPY,
      BLOCKHASH,
      COINBASE,
      TIMESTAMP,
      NUMBER,
      DIFFICULTY,
      GASLIMIT,
      POP,
      MLOAD,
      MSTORE,
      MSTORE8,
      SLOAD,
      SSTORE,
      JUMP,
      JUMPI,
      PC,
      MSIZE,
      GAS,
      JUMPDEST,
      CREATE,
      CALL,
      CALLCODE,
      RETURN,
      INVALID,
      SELFDESTRUCT
    )

  val HomesteadOpCodes: List[OpCode] =
    DELEGATECALL +: FrontierOpCodes
}

object OpCode {
  def sliceBytes(bytes: ByteVector, offset: UInt256, size: UInt256): ByteVector = {
    val start = offset.min(bytes.size).toLong
    val end = (offset + size).min(bytes.size).toLong
    val t = bytes.slice(start, end)
    if (size.toInt < t.size)
      t
    else
      t.padTo(size.toInt)
  }
}

/**
  * Base class for all the opcodes of the EVM
  *
  * @param code  Opcode byte representation
  * @param delta number of words to be popped from stack
  * @param alpha number of words to be pushed to stack
  */
sealed abstract class OpCode(
                              val code: Byte,
                              val delta: Int,
                              val alpha: Int,
                              val constGasFn: FeeSchedule => BigInt
                            ) {
  def execute[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] =
    if (state.stack.size < delta)
      state.withError(StackUnderflow).pure[F]
    else if (state.stack.size - delta + alpha > state.stack.maxSize)
      state.withError(StackOverflow).pure[F]
    else {
      val constGas: BigInt = constGasFn(state.config.feeSchedule)
      for {
        vg <- varGas(state)
        gas = constGas + vg
        s <- if (gas > state.gas) {
          state.copy(gas = 0).withError(OutOfGas).pure[F]
        } else {
          exec(state).map(_.spendGas(gas))
        }
      } yield s
    }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt]

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]]
}

sealed trait ConstGas {
  self: OpCode =>
  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = BigInt(0).pure[F]
}

sealed abstract class UnaryOp(code: Int, constGasFn: FeeSchedule => BigInt)(val f: UInt256 => UInt256)
  extends OpCode(code.toByte, 1, 1, constGasFn)
    with ConstGas {

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].delay {
    val (a, stack1) = state.stack.pop
    val res = f(a)
    val stack2 = stack1.push(res)
    state.withStack(stack2).step()
  }
}

sealed abstract class BinaryOp(code: Int, constGasFn: FeeSchedule => BigInt)(val f: (UInt256, UInt256) => UInt256)
  extends OpCode(code.toByte, 2, 1, constGasFn) {

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].delay {
    val (Seq(a, b), stack1) = state.stack.pop(2)
    val res = f(a, b)
    val stack2 = stack1.push(res)
    state.withStack(stack2).step()
  }
}

sealed abstract class TernaryOp(code: Int, constGasFn: FeeSchedule => BigInt)(
  val f: (UInt256, UInt256, UInt256) => UInt256)
  extends OpCode(code.toByte, 3, 1, constGasFn) {

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].delay {
    val (Seq(a, b, c), stack1) = state.stack.pop(3)
    val res = f(a, b, c)
    val stack2 = stack1.push(res)
    state.withStack(stack2).step()
  }
}

sealed abstract class ConstOp(code: Int) extends OpCode(code.toByte, 0, 1, _.G_base) with ConstGas {
  def f[F[_] : Sync](state: ProgramState[F]): UInt256

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].delay {
    val stack1 = state.stack.push(f(state))
    state.withStack(stack1).step()
  }
}

////////////////////////
// Stop and Arithmetic
////////////////////////
case object STOP extends OpCode(0x00, 0, 0, _.G_zero) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] =
    state.halt.pure[F]
}

case object ADD extends BinaryOp(0x01, _.G_verylow)(_ + _) with ConstGas

case object MUL extends BinaryOp(0x02, _.G_low)(_ * _) with ConstGas

case object SUB extends BinaryOp(0x03, _.G_verylow)(_ - _) with ConstGas

case object DIV extends BinaryOp(0x04, _.G_low)(_ div _) with ConstGas

case object SDIV extends BinaryOp(0x05, _.G_low)(_ sdiv _) with ConstGas

case object MOD extends BinaryOp(0x06, _.G_low)(_ mod _) with ConstGas

case object SMOD extends BinaryOp(0x07, _.G_low)(_ smod _) with ConstGas

case object ADDMOD extends TernaryOp(0x08, _.G_mid)(_.addmod(_, _)) with ConstGas

case object MULMOD extends TernaryOp(0x09, _.G_mid)(_.mulmod(_, _)) with ConstGas

case object EXP extends BinaryOp(0x0a, _.G_exp)(_ ** _) {
  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(_, m: UInt256), _) = state.stack.pop(2)
    (state.config.feeSchedule.G_expbyte * m.byteSize).pure[F]
  }
}

case object SIGNEXTEND extends BinaryOp(0x0b, _.G_low)((a, b) => b signExtend a) with ConstGas

/////////////////////////////////
// Comparison and Bitwise Logic
/////////////////////////////////
case object LT extends BinaryOp(0x10, _.G_verylow)(_ < _) with ConstGas

case object GT extends BinaryOp(0x11, _.G_verylow)(_ > _) with ConstGas

case object SLT extends BinaryOp(0x12, _.G_verylow)(_ slt _) with ConstGas

case object SGT extends BinaryOp(0x13, _.G_verylow)(_ sgt _) with ConstGas

case object EQ extends BinaryOp(0x14, _.G_verylow)(_ == _) with ConstGas

case object ISZERO extends UnaryOp(0x15, _.G_verylow)(_.isZero) with ConstGas

case object AND extends BinaryOp(0x16, _.G_verylow)(_ & _) with ConstGas

case object OR extends BinaryOp(0x17, _.G_verylow)(_ | _) with ConstGas

case object XOR extends BinaryOp(0x18, _.G_verylow)(_ ^ _) with ConstGas

case object NOT extends UnaryOp(0x19, _.G_verylow)(~_) with ConstGas

case object BYTE extends BinaryOp(0x1a, _.G_verylow)((a, b) => b getByte a) with ConstGas

/////////////////////
// SHA3
/////////////////////
case object SHA3 extends OpCode(0x20, 2, 1, _.G_sha3) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].delay {
    val (Seq(offset, size), stack1) = state.stack.pop(2)
    val (input, mem1) = state.memory.load(offset, size)
    val hash = ByteVector(input.toArray).kec256
    val ret = UInt256(hash)
    val stack2 = stack1.push(ret)
    state.withStack(stack2).withMemory(mem1).step()
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(offset, size), _) = state.stack.pop(2)
    val memCost = state.config.calcMemCost(state.memory.size, offset, size)
    val shaCost = state.config.feeSchedule.G_sha3word * wordsForBytes(size)
    (memCost + shaCost).pure[F]
  }
}

/////////////////////
// Env Information
/////////////////////
case object ADDRESS extends ConstOp(0x30) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.env.ownerAddr.toUInt256
}

case object BALANCE extends OpCode(0x31, 1, 1, _.G_balance) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (accountAddress, stack1) = state.stack.pop
    for {
      accountBalance <- state.world.getBalance(Address(accountAddress mod UInt256(BigInt(2).pow(160))))
      stack2 = stack1.push(accountBalance)
      s = state.withStack(stack2).step()
    } yield s
  }
}

case object ORIGIN extends ConstOp(0x32) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.env.originAddr.toUInt256
}

case object CALLER extends ConstOp(0x33) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.env.callerAddr.toUInt256
}

case object CALLVALUE extends ConstOp(0x34) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.env.value
}

case object CALLDATALOAD extends OpCode(0x35, 1, 1, _.G_verylow) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (offset, stack1) = state.stack.pop
    val data = OpCode.sliceBytes(state.inputData, offset, 32)
    val stack2 = stack1.push(UInt256(data))
    state.withStack(stack2).step().pure[F]
  }
}

case object CALLDATASIZE extends ConstOp(0x36) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.inputData.size
}

case object CALLDATACOPY extends OpCode(0x37, 3, 0, _.G_verylow) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (Seq(memOffset, dataOffset, size), stack1) = state.stack.pop(3)
    val data = OpCode.sliceBytes(state.inputData, dataOffset, size)
    val mem1 = state.memory.store(memOffset, data)
    state.withStack(stack1).withMemory(mem1).step().pure[F]
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(offset, _, size), _) = state.stack.pop(3)
    val memCost = state.config.calcMemCost(state.memory.size, offset, size)
    val copyCost = state.config.feeSchedule.G_copy * wordsForBytes(size)
    (memCost + copyCost).pure[F]
  }
}

case object CODESIZE extends ConstOp(0x38) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.env.program.length
}

case object CODECOPY extends OpCode(0x39, 3, 0, _.G_verylow) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (Seq(memOffset, codeOffset, size), stack1) = state.stack.pop(3)
    val bytes = OpCode.sliceBytes(state.program.code, codeOffset, size)
    val mem1 = state.memory.store(memOffset, bytes)
    state.withStack(stack1).withMemory(mem1).step().pure[F]
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(offset, _, size), _) = state.stack.pop(3)
    val memCost = state.config.calcMemCost(state.memory.size, offset, size)
    val copyCost = state.config.feeSchedule.G_copy * wordsForBytes(size)
    (memCost + copyCost).pure[F]
  }
}

case object GASPRICE extends ConstOp(0x3a.toByte) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.env.gasPrice
}

case object EXTCODESIZE extends OpCode(0x3b.toByte, 1, 1, _.G_extcode) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (addr, stack1) = state.stack.pop
    state.world.getCode(Address(addr)).map { code =>
      val codeSize = code.size
      val stack2 = stack1.push(UInt256(codeSize))
      state.withStack(stack2).step()
    }
  }
}

case object EXTCODECOPY extends OpCode(0x3c.toByte, 4, 0, _.G_extcode) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (Seq(address, memOffset, codeOffset, size), stack1) = state.stack.pop(4)
    state.world.getCode(Address(address)).map { code =>
      val codeCopy = OpCode.sliceBytes(code, codeOffset, size)
      val mem1 = state.memory.store(memOffset, codeCopy)
      state.withStack(stack1).withMemory(mem1).step()
    }
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(_, memOffset, _, size), _) = state.stack.pop(4)
    val memCost = state.config.calcMemCost(state.memory.size, memOffset, size)
    val copyCost = state.config.feeSchedule.G_copy * wordsForBytes(size)
    (memCost + copyCost).pure[F]
  }
}

/////////////////////
// Block Information
/////////////////////
case object BLOCKHASH extends OpCode(0x40, 1, 1, _.G_blockhash) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (blockNumber, stack1) = state.stack.pop

    val outOfLimits = state.env.blockHeader.number - blockNumber > 256 || blockNumber >= state.env.blockHeader.number
    for {
      hash <- if (outOfLimits) UInt256.Zero.pure[F]
      else OptionT(state.world.getBlockHash(blockNumber)).getOrElse(UInt256.Zero)
      stack2 = stack1.push(hash)
    } yield state.withStack(stack2).step()
  }
}

case object COINBASE extends ConstOp(0x41) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = UInt256(state.env.blockHeader.beneficiary)
}

case object TIMESTAMP extends ConstOp(0x42) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = UInt256(state.env.blockHeader.unixTimestamp)
}

case object NUMBER extends ConstOp(0x43) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = UInt256(state.env.blockHeader.number)
}

case object DIFFICULTY extends ConstOp(0x44) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = UInt256(state.env.blockHeader.difficulty)
}

case object GASLIMIT extends ConstOp(0x45) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = UInt256(state.env.blockHeader.gasLimit)
}

////////////////////////////////////////////////
// Stack, Memory, Storage, and Flow Operations
////////////////////////////////////////////////
case object POP extends OpCode(0x50, 1, 0, _.G_base) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (_, stack1) = state.stack.pop
    state.withStack(stack1).step().pure[F]
  }
}

case object MLOAD extends OpCode(0x51, 1, 1, _.G_verylow) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (offset, stack1) = state.stack.pop
    val (word, mem1) = state.memory.load(offset)
    val stack2 = stack1.push(word)
    state.withStack(stack2).withMemory(mem1).step().pure[F]
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (offset, _) = state.stack.pop
    state.config.calcMemCost(state.memory.size, offset, UInt256.Size).pure[F]
  }
}

case object MSTORE extends OpCode(0x52, 2, 0, _.G_verylow) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (Seq(offset, value), stack1) = state.stack.pop(2)
    val updatedMem = state.memory.store(offset, value)
    state.withStack(stack1).withMemory(updatedMem).step().pure[F]
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (offset, _) = state.stack.pop
    state.config.calcMemCost(state.memory.size, offset, UInt256.Size).pure[F]
  }
}

case object SLOAD extends OpCode(0x54, 1, 1, _.G_sload) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (offset, stack1) = state.stack.pop
    for {
      storage <- state.storage
      value <- storage.load(offset)
    } yield {
      val stack2 = stack1.push(value)
      state.withStack(stack2).step()
    }
  }
}

case object MSTORE8 extends OpCode(0x53, 2, 0, _.G_verylow) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].delay {
    val (Seq(offset, value), stack1) = state.stack.pop(2)
    val valueToByte = (value mod 256).toByte
    val updatedMem = state.memory.store(offset, valueToByte)
    state.withStack(stack1).withMemory(updatedMem).step()
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (offset, _) = state.stack.pop
    state.config.calcMemCost(state.memory.size, offset, 1).pure[F]
  }
}

case object SSTORE extends OpCode(0x55, 2, 0, _.G_zero) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (Seq(offset, value), stack1) = state.stack.pop(2)
    for {
      storage <- state.storage
      oldValue <- storage.load(offset)
      refund: BigInt = if (value.isZero && !oldValue.isZero) state.config.feeSchedule.R_sclear else 0
      updatedStorage = storage.store(offset, value)
    } yield {
      state.withStack(stack1).withStorage(updatedStorage).refundGas(refund).step()
    }
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(offset, value), _) = state.stack.pop(2)
    for {
      storage <- state.storage
      oldValue <- storage.load(offset)
    } yield {
      if (oldValue.isZero && !value.isZero) state.config.feeSchedule.G_sset else state.config.feeSchedule.G_sreset
    }
  }
}

case object JUMP extends OpCode(0x56, 1, 0, _.G_mid) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val (pos, stack1) = state.stack.pop
    val dest = pos.toInt // fail with InvalidJump if convertion to Int is lossy

    if (pos == UInt256(dest) && state.program.validJumpDestinations.contains(dest))
      state.withStack(stack1).goto(dest)
    else
      state.withError(InvalidJump(pos))
  }
}

case object JUMPI extends OpCode(0x57, 2, 0, _.G_high) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val (Seq(pos, cond), stack1) = state.stack.pop(2)
    val dest = pos.toInt // fail with InvalidJump if convertion to Int is lossy

    if (cond.isZero)
      state.withStack(stack1).step()
    else if (pos == UInt256(dest) && state.program.validJumpDestinations.contains(dest))
      state.withStack(stack1).goto(dest)
    else
      state.withError(InvalidJump(pos))
  }
}

case object PC extends ConstOp(0x58) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 = state.pc
}

case object MSIZE extends ConstOp(0x59) {

  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 =
    (BigInt(UInt256.Size) * wordsForBytes(state.memory.size)).toUInt256
}

case object GAS extends ConstOp(0x5a) {
  override def f[F[_] : Sync](state: ProgramState[F]): UInt256 =
    (state.gas - state.config.feeSchedule.G_base).toUInt256
}

case object JUMPDEST extends OpCode(0x5b.toByte, 0, 0, _.G_jumpdest) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] =
    state.step().pure[F]
}

///////////////////////////
// Push Operations
///////////////////////////
sealed abstract class PushOp(code: Int) extends OpCode(code.toByte, 0, 1, _.G_verylow) with ConstGas {
  val i: Int = code - 0x60

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val n = i + 1
    val bytes = state.program.getBytes(state.pc + 1, n)
    val word = UInt256(bytes)
    val stack1 = state.stack.push(word)
    state.withStack(stack1).step(n + 1)
  }
}

case object PUSH1 extends PushOp(0x60)

case object PUSH2 extends PushOp(0x61)

case object PUSH3 extends PushOp(0x62)

case object PUSH4 extends PushOp(0x63)

case object PUSH5 extends PushOp(0x64)

case object PUSH6 extends PushOp(0x65)

case object PUSH7 extends PushOp(0x66)

case object PUSH8 extends PushOp(0x67)

case object PUSH9 extends PushOp(0x68)

case object PUSH10 extends PushOp(0x69)

case object PUSH11 extends PushOp(0x6a)

case object PUSH12 extends PushOp(0x6b)

case object PUSH13 extends PushOp(0x6c)

case object PUSH14 extends PushOp(0x6d)

case object PUSH15 extends PushOp(0x6e)

case object PUSH16 extends PushOp(0x6f)

case object PUSH17 extends PushOp(0x70)

case object PUSH18 extends PushOp(0x71)

case object PUSH19 extends PushOp(0x72)

case object PUSH20 extends PushOp(0x73)

case object PUSH21 extends PushOp(0x74)

case object PUSH22 extends PushOp(0x75)

case object PUSH23 extends PushOp(0x76)

case object PUSH24 extends PushOp(0x77)

case object PUSH25 extends PushOp(0x78)

case object PUSH26 extends PushOp(0x79)

case object PUSH27 extends PushOp(0x7a)

case object PUSH28 extends PushOp(0x7b)

case object PUSH29 extends PushOp(0x7c)

case object PUSH30 extends PushOp(0x7d)

case object PUSH31 extends PushOp(0x7e)

case object PUSH32 extends PushOp(0x7f)

///////////////////////////
// Duplicate Operations
///////////////////////////
sealed abstract class DupOp private(code: Int, val i: Int)
  extends OpCode(code.toByte, i + 1, i + 2, _.G_verylow)
    with ConstGas {
  def this(code: Int) = this(code, code - 0x80)

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val stack1 = state.stack.dup(i)
    state.withStack(stack1).step()
  }
}

case object DUP1 extends DupOp(0x80)

case object DUP2 extends DupOp(0x81)

case object DUP3 extends DupOp(0x82)

case object DUP4 extends DupOp(0x83)

case object DUP5 extends DupOp(0x84)

case object DUP6 extends DupOp(0x85)

case object DUP7 extends DupOp(0x86)

case object DUP8 extends DupOp(0x87)

case object DUP9 extends DupOp(0x88)

case object DUP10 extends DupOp(0x89)

case object DUP11 extends DupOp(0x8a)

case object DUP12 extends DupOp(0x8b)

case object DUP13 extends DupOp(0x8c)

case object DUP14 extends DupOp(0x8d)

case object DUP15 extends DupOp(0x8e)

case object DUP16 extends DupOp(0x8f)

///////////////////////////
// Exchange Operations
///////////////////////////
sealed abstract class SwapOp(code: Int, val i: Int)
  extends OpCode(code.toByte, i + 2, i + 2, _.G_verylow)
    with ConstGas {
  def this(code: Int) = this(code, code - 0x90)

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val stack1 = state.stack.swap(i + 1)
    state.withStack(stack1).step()
  }
}

case object SWAP1 extends SwapOp(0x90)

case object SWAP2 extends SwapOp(0x91)

case object SWAP3 extends SwapOp(0x92)

case object SWAP4 extends SwapOp(0x93)

case object SWAP5 extends SwapOp(0x94)

case object SWAP6 extends SwapOp(0x95)

case object SWAP7 extends SwapOp(0x96)

case object SWAP8 extends SwapOp(0x97)

case object SWAP9 extends SwapOp(0x98)

case object SWAP10 extends SwapOp(0x99)

case object SWAP11 extends SwapOp(0x9a)

case object SWAP12 extends SwapOp(0x9b)

case object SWAP13 extends SwapOp(0x9c)

case object SWAP14 extends SwapOp(0x9d)

case object SWAP15 extends SwapOp(0x9e)

case object SWAP16 extends SwapOp(0x9f)

///////////////////////////
// Logging
///////////////////////////
sealed abstract class LogOp(code: Int, val i: Int) extends OpCode(code.toByte, i + 2, 0, _.G_log) {
  def this(code: Int) = this(code, code - 0xa0)

  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val (Seq(offset, size, topics@_*), stack1) = state.stack.pop(delta)
    val (data, memory) = state.memory.load(offset, size)
    val logEntry = TxLogEntry(state.env.ownerAddr, topics.map(_.bytes).toList, data)

    state.withStack(stack1).withMemory(memory).withLog(logEntry).step()
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = Sync[F].pure {
    val (Seq(offset, size, _*), _) = state.stack.pop(delta)
    val memCost = state.config.calcMemCost(state.memory.size, offset, size)
    val logCost = state.config.feeSchedule.G_logdata * size + i * state.config.feeSchedule.G_logtopic
    memCost + logCost
  }
}

case object LOG0 extends LogOp(0xa0)

case object LOG1 extends LogOp(0xa1)

case object LOG2 extends LogOp(0xa2)

case object LOG3 extends LogOp(0xa3)

case object LOG4 extends LogOp(0xa4)

///////////////////////////
// System
///////////////////////////
abstract class CreateOp extends OpCode(0xf0.toByte, 3, 1, _.G_create) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (Seq(endowment, inOffset, inSize), stack1) = state.stack.pop(3)

    val validCall =
      (state.env.callDepth < EvmConfig.MaxCallDepth).pure[F] && state.ownBalance.map(_ >= endowment)

    Sync[F].ifM(validCall)(
      ifTrue = {
        val (initCode, memory1) = state.memory.load(inOffset, inSize)

        for {
          (newAddress, world1) <- state.world.createAddressWithOpCode(state.env.ownerAddr)
          world2 <- world1.initialiseAccount(newAddress).flatMap(_.transfer(state.env.ownerAddr, newAddress, endowment))
          conflict <- state.world.nonEmptyCodeOrNonceAccount(newAddress)
          code = if (conflict) ByteVector(INVALID.code) else initCode
          newEnv = state.env.copy(
            callerAddr = state.env.ownerAddr,
            ownerAddr = newAddress,
            value = endowment,
            program = Program(code),
            inputData = ByteVector.empty,
            callDepth = state.env.callDepth + 1
          )

          //FIXME: to avoid calculating this twice, we could adjust state.gas prior to execution in OpCode#execute
          //not sure how this would affect other opcodes [EC-243]
          vg <- varGas(state)
          availableGas = state.gas - (constGasFn(state.config.feeSchedule) + vg)
          startGas = state.config.gasCap(availableGas)

          context = ProgramContext[F](newEnv, newAddress, startGas, world2, state.config, state.addressesToDelete)
          result <- VM.run(context)

          contractCode = result.returnData
          codeDepositGas = state.config.calcCodeDepositCost(contractCode)
          gasUsedInVm = startGas - result.gasRemaining
          totalGasRequired = gasUsedInVm + codeDepositGas

          maxCodeSizeExceeded = state.config.maxCodeSize.exists(codeSizeLimit => contractCode.size > codeSizeLimit)
          enoughGasForDeposit = totalGasRequired <= startGas

          creationFailed = maxCodeSizeExceeded || result.error.isDefined ||
            !enoughGasForDeposit && state.config.exceptionalFailedCodeDeposit

        } yield {
          if (state.env.noSelfCall) {
            val stack2 = stack1.push(newAddress.toUInt256)
            state
              .withStack(stack2)
              .step()
          } else {
            if (creationFailed) {
              val stack2 = stack1.push(UInt256.Zero)
              state
                .withWorld(world1) // if creation fails at this point we still leave the creators nonce incremented
                .withStack(stack2)
                .spendGas(startGas)
                .step()
            } else {
              val stack2 = stack1.push(newAddress.toUInt256)
              val state1 =
                if (!enoughGasForDeposit)
                  state.withWorld(result.world).spendGas(gasUsedInVm)
                else {
                  val world3 = result.world.saveCode(newAddress, result.returnData)
                  val internalTx = InternalTransaction(CREATE, state.env.ownerAddr, None, startGas, initCode, endowment)
                  state.withWorld(world3).spendGas(totalGasRequired).withInternalTxs(internalTx +: result.internalTxs)
                }

              state1
                .refundGas(result.gasRefund)
                .withStack(stack2)
                .withAddressesToDelete(result.addressesToDelete)
                .withLogs(result.logs)
                .withMemory(memory1)
                .step()
            }
          }
        }
      },
      ifFalse = {
        val stack2 = stack1.push(UInt256.Zero)
        state.withStack(stack2).step().pure[F]
      }
    )
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = Sync[F].pure {
    val (Seq(_, inOffset, inSize), _) = state.stack.pop(3)
    state.config.calcMemCost(state.memory.size, inOffset, inSize)
  }
}

case object CREATE extends CreateOp

abstract class CallOp(code: Int, delta: Int, alpha: Int) extends OpCode(code.toByte, delta, alpha, _.G_zero) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (params@Seq(_, to, callValue, inOffset, inSize, outOffset, outSize), stack1) = getParams(state)

    val (inputData, mem1) = state.memory.load(inOffset, inSize)
    val endowment = if (this == DELEGATECALL) UInt256.Zero else callValue
    val toAddr = Address(to)

    if (state.env.noSelfCall) {
      for {
        startGas <- calcStartGas(state, params, endowment)
      } yield {
        val stack2 = stack1.push(UInt256.One)

        state
          .withStack(stack2)
          .withMemory(state.memory.expand(outOffset, outSize))
          .spendGas(-startGas)
          .step()
      }
    } else {
      for {
        startGas <- calcStartGas(state, params, endowment)
        (world1, owner, caller) <- this match {
          case CALL =>
            state.world
              .transfer(state.ownAddress, toAddr, endowment)
              .map(withTransfer => (withTransfer, toAddr, state.ownAddress))

          case CALLCODE =>
            (state.world, state.ownAddress, state.ownAddress).pure[F]

          case DELEGATECALL =>
            (state.world, state.ownAddress, state.env.callerAddr).pure[F]
        }

        code <- world1.getCode(toAddr)
        ownBalance <- state.ownBalance
        env = state.env.copy(
          ownerAddr = owner,
          callerAddr = caller,
          inputData = inputData,
          value = callValue,
          program = Program(code),
          callDepth = state.env.callDepth + 1
        )

        context: ProgramContext[F] = state.context.copy(
          env = env,
          receivingAddr = toAddr,
          startGas = startGas,
          world = world1,
          initialAddressesToDelete = state.addressesToDelete
        )

        result <- VM.run(context)
      } yield {
        val validCall = state.env.callDepth < EvmConfig.MaxCallDepth && endowment <= ownBalance

        if (!validCall || result.error.isDefined) {
          val stack2 = stack1.push(UInt256.Zero)

          val gasAdjustment: BigInt = if (validCall) 0 else -startGas
          val mem2 = mem1.expand(outOffset, outSize)

          val world1 = if (validCall) state.world.combineTouchedAccounts(result.world) else state.world

          state
            .withStack(stack2)
            .withMemory(mem2)
            .withWorld(world1)
            .spendGas(gasAdjustment)
            .step()

        } else {
          val stack2 = stack1.push(UInt256.One)
          val sizeCap = outSize.min(result.returnData.size).toInt
          val output = result.returnData.take(sizeCap)
          val mem2 = mem1.store(outOffset, output).expand(outOffset, outSize)
          val internalTx = internalTransaction(state.env, to, startGas, inputData, endowment)

          state
            .spendGas(-result.gasRemaining)
            .refundGas(result.gasRefund)
            .withStack(stack2)
            .withMemory(mem2)
            .withWorld(result.world)
            .withAddressesToDelete(result.addressesToDelete)
            .withInternalTxs(internalTx +: result.internalTxs)
            .withLogs(result.logs)
            .step()
        }
      }
    }
  }

  def internalTransaction(
                           env: ExecEnv,
                           callee: UInt256,
                           startGas: BigInt,
                           inputData: ByteVector,
                           endowment: UInt256
                         ): InternalTransaction = {
    val from = env.ownerAddr
    val to = if (this == CALL) Address(callee) else env.ownerAddr
    InternalTransaction(this, from, Some(to), startGas, inputData, endowment)
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (Seq(gas, to, callValue, inOffset, inSize, outOffset, outSize), _) = getParams(state)
    val endowment = if (this == DELEGATECALL) UInt256.Zero else callValue
    val memCost = calcMemCost(state, inOffset, inSize, outOffset, outSize)

    // FIXME: these are calculated twice (for gas and exec), especially account existence. Can we do better? [EC-243]
    for {
      gExtra <- gasExtra(state, endowment, Address(to))
    } yield {
      val gCap: BigInt = gasCap(state, gas, gExtra + memCost)
      memCost + gCap + gExtra
    }
  }

  def calcMemCost[F[_] : Sync](
                                state: ProgramState[F],
                                inOffset: UInt256,
                                inSize: UInt256,
                                outOffset: UInt256,
                                outSize: UInt256
                              ): BigInt = {

    val memCostIn = state.config.calcMemCost(state.memory.size, inOffset, inSize)
    val memCostOut = state.config.calcMemCost(state.memory.size, outOffset, outSize)
    memCostIn max memCostOut
  }

  def getParams[F[_] : Sync](state: ProgramState[F]): (Seq[UInt256], Stack) = {
    val (Seq(gas, to), stack1) = state.stack.pop(2)
    val (value, stack2) = if (this == DELEGATECALL) (state.env.value, stack1) else stack1.pop
    val (Seq(inOffset, inSize, outOffset, outSize), stack3) = stack2.pop(4)
    Seq(gas, to, value, inOffset, inSize, outOffset, outSize) -> stack3
  }

  def calcStartGas[F[_] : Sync](state: ProgramState[F], params: Seq[UInt256], endowment: UInt256): F[BigInt] = {
    val Seq(gas, to, _, inOffset, inSize, outOffset, outSize) = params
    val memCost = calcMemCost(state, inOffset, inSize, outOffset, outSize)

    for {
      gExtra <- gasExtra(state, endowment, Address(to))
    } yield {
      val gCap = gasCap(state, gas, gExtra + memCost)
      if (endowment.isZero) gCap else gCap + state.config.feeSchedule.G_callstipend
    }
  }

  private def gasCap[F[_] : Sync](state: ProgramState[F], g: BigInt, consumedGas: BigInt): BigInt =
    if (state.config.subGasCapDivisor.isDefined && state.gas >= consumedGas)
      g min state.config.gasCap(state.gas - consumedGas)
    else
      g

  private def gasExtra[F[_] : Sync](state: ProgramState[F], endowment: UInt256, to: Address): F[BigInt] = {
    val isValueTransfer = endowment > 0

    def postEip161CostCondition: F[Boolean] =
      state.world.isAccountDead(to) && (this == CALL).pure[F] && isValueTransfer.pure[F]

    def preEip161CostCondition: F[Boolean] =
      !state.world.accountExists(to) && (this == CALL).pure[F]

    for {
      b <- state.config.noEmptyAccounts.pure[F] && postEip161CostCondition || !state.config.noEmptyAccounts
        .pure[F] && preEip161CostCondition
      c_new: BigInt = if (b) state.config.feeSchedule.G_newaccount else 0
      c_xfer: BigInt = if (endowment.isZero) 0 else state.config.feeSchedule.G_callvalue
    } yield state.config.feeSchedule.G_call + c_xfer + c_new
  }
}

case object CALL extends CallOp(0xf1, 7, 1)

case object CALLCODE extends CallOp(0xf2, 7, 1)

case object DELEGATECALL extends CallOp(0xf4, 6, 1)

case object RETURN extends OpCode(0xf3.toByte, 2, 0, _.G_zero) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = Sync[F].pure {
    val (Seq(offset, size), stack1) = state.stack.pop(2)
    val (ret, mem1) = state.memory.load(offset, size)
    state.withStack(stack1).withReturnData(ret).withMemory(mem1).halt
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = Sync[F].pure {
    val (Seq(offset, size), _) = state.stack.pop(2)
    state.config.calcMemCost(state.memory.size, offset, size)
  }
}

case object INVALID extends OpCode(0xfe.toByte, 0, 0, _.G_zero) with ConstGas {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] =
    state.withError(InvalidOpCode(code)).pure[F]
}

case object SELFDESTRUCT extends OpCode(0xff.toByte, 1, 0, _.G_selfdestruct) {
  def exec[F[_] : Sync](state: ProgramState[F]): F[ProgramState[F]] = {
    val (refund, stack1) = state.stack.pop
    val refundAddr: Address = Address(refund)
    val gasRefund: BigInt =
      if (state.addressesToDelete contains state.ownAddress) 0 else state.config.feeSchedule.R_selfdestruct

    for {
      ownBalance <- state.ownBalance
      world <- if (state.ownAddress == refundAddr)
        state.world.removeAllEther(state.ownAddress)
      else
        state.world.transfer(state.ownAddress, refundAddr, ownBalance)
    } yield {
      state
        .withWorld(world)
        .refundGas(gasRefund)
        .withAddressToDelete(state.ownAddress)
        .withStack(stack1)
        .halt
    }
  }

  def varGas[F[_] : Sync](state: ProgramState[F]): F[BigInt] = {
    val (refundAddr, _) = state.stack.pop
    val refundAddress = Address(refundAddr)

    val isValueTransfer = state.ownBalance.map(_ > 0)

    def postEip161CostCondition: F[Boolean] =
      state.config.chargeSelfDestructForNewAccount.pure[F] &&
        isValueTransfer &&
        state.world.isAccountDead(refundAddress)

    def preEip161CostCondition: F[Boolean] =
      state.config.chargeSelfDestructForNewAccount.pure[F] && !state.world.accountExists(refundAddress)

    for {
      b <- state.config.noEmptyAccounts.pure[F] && postEip161CostCondition || !state.config.noEmptyAccounts
        .pure[F] && preEip161CostCondition
    } yield {
      if (b) state.config.feeSchedule.G_newaccount
      else 0
    }
  }
}
