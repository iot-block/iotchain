package jbok.evm

import cats.effect.Sync
import jbok.core.models._
import jbok.crypto._
import jbok.crypto.signature.CryptoSignature
import jbok.crypto.signature.ecdsa.SecP256k1
import scodec.bits.ByteVector

object PrecompiledContracts {

  val EcDsaRecAddr = Address(1)
  val Sha256Addr   = Address(2)
  val Rip160Addr   = Address(3)
  val IdAddr       = Address(4)

  val contracts = Map(
    EcDsaRecAddr -> EllipticCurveRecovery,
    Sha256Addr   -> Sha256,
    Rip160Addr   -> Ripemd160,
    IdAddr       -> Identity
  )

  def runOptionally[F[_]: Sync](context: ProgramContext[F]): Option[ProgramResult[F]] =
    contracts.get(context.receivingAddr).map(_.run(context))

  sealed trait PrecompiledContract {
    protected def exec(inputData: ByteVector): ByteVector
    protected def gas(inputDataSize: UInt256): BigInt

    def run[F[_]: Sync](context: ProgramContext[F]): ProgramResult[F] = {
      val g = gas(context.env.inputData.size)

      val (result, error, gasRemaining): (ByteVector, Option[ProgramError], BigInt) =
        if (g <= context.startGas)
          (exec(context.env.inputData), None, context.startGas - g)
        else
          (ByteVector.empty, Some(OutOfGas), 0)

      ProgramResult(
        result,
        gasRemaining,
        context.world,
        Set.empty,
        Nil,
        Nil,
        0,
        error
      )
    }
  }

  object EllipticCurveRecovery extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector = {
      val data = inputData.padRight(128)
      val h    = data.slice(0, 32)
      val v    = data.slice(32, 64)
      val r    = data.slice(64, 96)
      val s    = data.slice(96, 128)

      if (hasOnlyLastByteSet(v)) {
        val sig       = CryptoSignature(r.toArray ++ s.toArray ++ Array(v.last))
        val recovered = SecP256k1.recoverPublic(h.toArray, sig)
        recovered
          .map { pk =>
            val hash = pk.bytes.kec256.slice(12, 32)
            hash.padLeft(32)
          }
          .getOrElse(ByteVector.empty)
      } else
        ByteVector.empty
    }

    def gas(inputDataSize: UInt256): BigInt =
      3000

    private def hasOnlyLastByteSet(v: ByteVector): Boolean =
      v.dropWhile(_ == 0).size == 1
  }

  object Sha256 extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector =
      inputData.sha256

    def gas(inputDataSize: UInt256): BigInt =
      60 + 12 * wordsForBytes(inputDataSize)
  }

  object Ripemd160 extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector =
      inputData.ripemd160

    def gas(inputDataSize: UInt256): BigInt =
      600 + 120 * wordsForBytes(inputDataSize)
  }

  object Identity extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector =
      inputData

    def gas(inputDataSize: UInt256): BigInt =
      15 + 3 * wordsForBytes(inputDataSize)
  }
}
