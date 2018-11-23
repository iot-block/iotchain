package jbok.benchmark.evm

import jbok.benchmark.JbokBenchmark
import jbok.core.models.{Address, UInt256}
import jbok.evm.CreateOpFixture.initWorld
import jbok.evm._
import org.openjdk.jmh.annotations.Benchmark
import scodec.bits._
import jbok.evm.testkit._

class OpCodeBenchmark extends JbokBenchmark {
  val config = EvmConfig.PostEIP161ConfigBuilder(None)
  val env =
    ExecEnv(ownerAddr, callerAddr, callerAddr, 0, ByteVector.empty, 0, Program(ByteVector.empty), exampleBlockHeader, 0)
  val context = ProgramContext(env, Address(1024), UInt256.MaxValue, initWorld, config)
  val state   = ProgramState(context)

  val a = UInt256(hex"802431afcbce1fc194c9eaa417b2fb67dc75a95db0bc7ec6b1c8af11df6a1da9")
  val b = UInt256(hex"a1f5aac137876480252e5dcac62c354ec0d42b76b0642b6181ed099849ea1d57")
  val c = UInt256(hex"ff3f9014f20db29ae04af2c2d265de17")
  val d = UInt256(255)

  @Benchmark
  def add() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = ADD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def add128() = {
    val stack = Stack
      .empty()
      .push(UInt256(hex"802431afcbce1fc194c9eaa417b2fb"))
      .push(UInt256(hex"a1f5aac137876480252e5dcac62c35"))
    val stateIn  = state.copy(stack = stack)
    val stateOut = ADD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sub() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SUB.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def mul() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = MUL.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def div() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(a)
    val stateIn  = state.copy(stack = stack)
    val stateOut = DIV.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sdiv() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(a)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SDIV.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def mod() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = MOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def smod() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SMOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def exp() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = EXP.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def signExtend() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SIGNEXTEND.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def lt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = LT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def slt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SLT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def gt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = GT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sgt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SGT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def eq() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = EQ.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def and() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = AND.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def or() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = OR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def xor() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = XOR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def opByte() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn  = state.copy(stack = stack)
    val stateOut = BYTE.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def addMod() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(b)
      .push(a)
    val stateIn  = state.copy(stack = stack)
    val stateOut = ADDMOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def mulMod() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(b)
      .push(a)
    val stateIn  = state.copy(stack = stack)
    val stateOut = MULMOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def shl() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(d)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SHL.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def shr() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(d)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SHR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sar() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(d)
    val stateIn  = state.copy(stack = stack)
    val stateOut = SAR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def isZero() = {
    val stack = Stack
      .empty()
      .push(a)
    val stateIn  = state.copy(stack = stack)
    val stateOut = ISZERO.execute(stateIn).unsafeRunSync()
  }

}
