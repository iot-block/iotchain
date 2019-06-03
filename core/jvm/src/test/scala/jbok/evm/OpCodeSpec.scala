package jbok.evm

import cats.effect.IO
import jbok.common.math.N
import jbok.core.CoreSpec
import jbok.core.models.Address

trait OpCodeSpec extends CoreSpec {

  implicit val evmConfig: EvmConfig

  val testStackMaxSize = 32

  val ownerAddr = Address(0x123456)

  val callerAddr = Address(0xabcdef)

  val receiveAddr = Address(0x654321)

  lazy val unaryOps = evmConfig.opCodes.collect { case op: UnaryOp => op }

  lazy val binaryOps = evmConfig.opCodes.collect { case op: BinaryOp => op }

  lazy val ternaryOps = evmConfig.opCodes.collect { case op: TernaryOp => op }

  lazy val constOps = evmConfig.opCodes.collect { case op: ConstOp => op }

  lazy val pushOps = evmConfig.opCodes.collect { case op: PushOp => op }

  lazy val dupOps = evmConfig.opCodes.collect { case op: DupOp => op }

  lazy val swapOps = evmConfig.opCodes.collect { case op: SwapOp => op }

  lazy val logOps = evmConfig.opCodes.collect { case op: LogOp => op }

  lazy val constGasOps = evmConfig.opCodes.collect { case op: ConstGas if op != INVALID => op }

  def test[T <: OpCode](ops: T*)(f: T => Any): Unit =
    ops.foreach { op =>
      s"$op" in {
        f(op)
      }
    }

  def ignore[T <: OpCode](ops: T*)(f: T => Any): Unit =
    ops.foreach { op =>
      s"op" ignore {
        f(op)
      }
    }

  /**
    * Run this as the last test in the suite
    * Ignoring an OpCode test will NOT cause this test to fail
    */
  def verifyAllOpCodesRegistered(except: OpCode*): Unit =
    "all opcodes have been registered" in {
      val untested = evmConfig.opCodes.filterNot(op => testNames(op.toString)).diff(except)
      if (untested.isEmpty)
        succeed
      else
        fail("Unregistered opcodes: " + untested.mkString(", "))
    }

  def verifyGas(expectedGas: N, stateIn: ProgramState[IO], stateOut: ProgramState[IO], allowOOG: Boolean = true): Unit =
    if (stateOut.error.contains(OutOfGas) && allowOOG)
      stateIn.gas < expectedGas shouldBe true
    else if (stateOut.error.contains(OutOfGas) && !allowOOG)
      fail(s"Unexpected $OutOfGas error")
    else if (stateOut.error.isDefined && stateOut.error.collect { case InvalidJump(dest) => dest }.isEmpty)
      //Found error that is not an InvalidJump
      fail(s"Unexpected ${stateOut.error.get} error")
    else {
      stateOut.gas shouldBe (stateIn.gas - expectedGas)
    }
}
