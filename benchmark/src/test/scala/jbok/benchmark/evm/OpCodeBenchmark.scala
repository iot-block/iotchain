package jbok.benchmark.evm

import jbok.benchmark.JbokBenchmark
import jbok.core.CoreSpec
import jbok.core.models.{Address, UInt256}
import jbok.evm._
import org.openjdk.jmh.annotations.Benchmark
import scodec.bits._

class OpCodeBenchmark extends JbokBenchmark {
  val config      = EvmConfig.ConstantinopleConfigBuilder(None)
  val fixture     = CreateOpFixture(config)
  val header      = CoreSpec.genesis.header
  val ownerAddr   = Address(0x123456)
  val callerAddr  = Address(0xabcdef)
  val receiveAddr = Address(0x654321)
  val env         = ExecEnv(ownerAddr, callerAddr, callerAddr, 0, ByteVector.empty, 0, Program(ByteVector.empty), header, 0)
  val context     = ProgramContext(env, Address(1024), UInt256.MaxValue, fixture.initWorld, config)
  val state       = ProgramState(context)

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
    val stateIn = state.copy(stack = stack)
    ADD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def add128() = {
    val stack = Stack
      .empty()
      .push(UInt256(hex"802431afcbce1fc194c9eaa417b2fb"))
      .push(UInt256(hex"a1f5aac137876480252e5dcac62c35"))
    val stateIn = state.copy(stack = stack)
    ADD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sub() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    SUB.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def mul() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    MUL.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def div() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(a)
    val stateIn = state.copy(stack = stack)
    DIV.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sdiv() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(a)
    val stateIn = state.copy(stack = stack)
    SDIV.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def mod() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    MOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def smod() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    SMOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def exp() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    EXP.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def signExtend() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    SIGNEXTEND.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def lt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    LT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def slt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    SLT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def gt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    GT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sgt() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    SGT.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def eq() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    EQ.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def and() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    AND.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def or() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    OR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def xor() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    XOR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def opByte() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(b)
    val stateIn = state.copy(stack = stack)
    BYTE.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def addMod() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(b)
      .push(a)
    val stateIn = state.copy(stack = stack)
    ADDMOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def mulMod() = {
    val stack = Stack
      .empty()
      .push(c)
      .push(b)
      .push(a)
    val stateIn = state.copy(stack = stack)
    MULMOD.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def shl() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(d)
    val stateIn = state.copy(stack = stack)
    SHL.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def shr() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(d)
    val stateIn = state.copy(stack = stack)
    SHR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def sar() = {
    val stack = Stack
      .empty()
      .push(a)
      .push(d)
    val stateIn = state.copy(stack = stack)
    SAR.execute(stateIn).unsafeRunSync()
  }

  @Benchmark
  def isZero() = {
    val stack = Stack
      .empty()
      .push(a)
    val stateIn = state.copy(stack = stack)
    ISZERO.execute(stateIn).unsafeRunSync()
  }

}
