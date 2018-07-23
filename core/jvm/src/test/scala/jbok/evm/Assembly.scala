package jbok.evm

import jbok.evm.Assembly._
import scodec.bits.ByteVector

object Assembly {

  sealed trait ByteCode {
    def bytes: ByteVector
  }

  implicit class OpCodeAsByteCode(val op: OpCode) extends ByteCode {
    def bytes: ByteVector = ByteVector(op.code)
  }

  implicit class IntAsByteCode(val i: Int) extends ByteCode {
    def bytes: ByteVector = ByteVector(i.toByte)
  }

  implicit class ByteAsByteCode(val byte: Byte) extends ByteCode {
    def bytes: ByteVector = ByteVector(byte)
  }

  implicit class ByteVectorAsByteCode(val bytes: ByteVector) extends ByteCode
}

case class Assembly(byteCode: ByteCode*) {
  val code: ByteVector = byteCode.foldLeft(ByteVector.empty)(_.bytes ++ _.bytes)

  val program: Program = Program(code)

  def linearConstGas(config: EvmConfig): BigInt = byteCode.foldLeft(BigInt(0)) {
    case (g, b: OpCodeAsByteCode) => g + b.op.constGasFn(config.feeSchedule)
    case (g, _)                   => g
  }
}
