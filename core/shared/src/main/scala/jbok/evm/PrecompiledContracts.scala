package jbok.evm

import cats.effect.Sync
import cats.implicits._
import jbok.core.models._
import jbok.crypto._
import jbok.crypto.bn256.{BN256, CurvePoint, TwistPoint}
import jbok.crypto.signature.{CryptoSignature, ECDSA, Signature}
import scodec.bits.ByteVector

object PrecompiledContracts {

  val EcDsaRecAddr     = Address(1)
  val Sha256Addr       = Address(2)
  val Rip160Addr       = Address(3)
  val IdAddr           = Address(4)
  val ExpModAddr       = Address(5)
  val BN256AddAddr     = Address(6)
  val BN256MulAddr     = Address(7)
  val BN256PairingAddr = Address(8)

  val FrontierContracts = Map(
    EcDsaRecAddr -> EllipticCurveRecovery,
    Sha256Addr   -> Sha256,
    Rip160Addr   -> Ripemd160,
    IdAddr       -> Identity,
  )

  private val bn256Constracts = Map(
    ExpModAddr       -> ExpMod,
    BN256AddAddr     -> BN256Add,
    BN256MulAddr     -> BN256Mul,
    BN256PairingAddr -> BN256Pairing
  )

  val ByzantiumContracts = FrontierContracts ++ bn256Constracts

  def runOptionally[F[_]: Sync](contracts: Map[Address, PrecompiledContract],
                                context: ProgramContext[F]): Option[ProgramResult[F]] =
    contracts.get(context.receivingAddr).map(_.run(context))

  sealed trait PrecompiledContract {
    protected def exec(inputData: ByteVector): ByteVector
    protected def gas(inputData: ByteVector): BigInt

    def run[F[_]: Sync](context: ProgramContext[F]): ProgramResult[F] = {
      val g = gas(context.env.inputData)

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
      val data = if (inputData.length < 128) inputData.padRight(128) else inputData
      val h    = data.slice(0, 32)
      val v    = data.slice(32, 64)
      val r    = data.slice(64, 96)
      val s    = data.slice(96, 128)

      if (hasOnlyLastByteSet(v)) {
        val sig       = CryptoSignature(r.toArray ++ s.toArray ++ Array(v.last))
        val recovered = Signature[ECDSA].recoverPublic(h.toArray, sig, 0)
        recovered
          .map { pk =>
            val hash = pk.bytes.kec256.slice(12, 32)
            hash.padLeft(32)
          }
          .getOrElse(ByteVector.empty)
      } else
        ByteVector.empty
    }

    def gas(inputData: ByteVector): BigInt =
      3000

    private def hasOnlyLastByteSet(v: ByteVector): Boolean =
      v.dropWhile(_ == 0).size == 1
  }

  object Sha256 extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector =
      inputData.sha256

    def gas(inputData: ByteVector): BigInt =
      60 + 12 * wordsForBytes(inputData.size)
  }

  object Ripemd160 extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector =
      inputData.ripemd160

    def gas(inputData: ByteVector): BigInt =
      600 + 120 * wordsForBytes(inputData.size)
  }

  object Identity extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector =
      inputData

    def gas(inputData: ByteVector): BigInt =
      15 + 3 * wordsForBytes(inputData.size)
  }

  object ExpMod extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector = {
      val data    = if (inputData.length < 96) inputData.padRight(96) else inputData
      val baseLen = BigInt(1, data.slice(0, 32).toArray).toLong
      val expLen  = BigInt(1, data.slice(32, 64).toArray).toLong
      val modLen  = BigInt(1, data.slice(64, 96).toArray).toLong

      if (baseLen == 0 && modLen == 0) {
        ByteVector.empty
      } else {
        val inputT = if (data.length > 96) data.drop(96) else ByteVector.empty
        val input =
          if (inputT.length < baseLen + expLen + modLen) inputT.padRight(baseLen + expLen + modLen) else inputT
        val base = if (baseLen != 0) BigInt(1, input.slice(0, baseLen).toArray) else BigInt(0)
        val exp  = if (expLen != 0) BigInt(1, input.slice(baseLen, baseLen + expLen).toArray) else BigInt(0)
        val mod =
          if (modLen != 0) BigInt(1, input.slice(baseLen + expLen, baseLen + expLen + modLen).toArray) else BigInt(0)

        if (mod == 0) {
          ByteVector.empty
        } else {
          val r = ByteVector(base.modPow(exp, mod).toByteArray)
          if (r.length < modLen) r.padLeft(modLen) else r.takeRight(modLen)
        }
      }
    }

    def gas(inputData: ByteVector): BigInt = {
      val data    = if (inputData.length < 96) inputData.padRight(96) else inputData
      val baseLen = BigInt(data.slice(0, 32).toArray).toLong
      val expLen  = BigInt(data.slice(32, 64).toArray).toLong
      val modLen  = BigInt(data.slice(64, 96).toArray).toLong

      val input = if (data.length > 96) data.drop(96) else ByteVector.empty

      def f(x: Long): Long = x match {
        case x if x <= 64   => x * x
        case x if x <= 1024 => x * x / 4 + 96 * x - 3072
        case x              => x * x / 16 + 480 * x - 199680
      }

      val expHead: BigInt =
        if (input.length <= baseLen) 0
        else BigInt(1, input.slice(baseLen, baseLen + expLen.min(32)).toArray)

      val msb       = if (expHead.bitLength > 0) expHead.bitLength - 1 else 0
      val adjExpLen = (if (expLen > 32) (expLen + 32) * 8 else 0) + msb

      val gas = f(modLen.max(baseLen)) * adjExpLen.max(1) / 20
      if (gas >= BigInt(2).pow(65)) BigInt(2).pow(65) - 1
      else gas
    }
  }

  object BN256Add extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector = {
      val data = if (inputData.length < 128) inputData.padRight(128) else inputData
      val x1   = data.slice(0, 32).toArray
      val y1   = data.slice(32, 64).toArray
      val x2   = data.slice(64, 96).toArray
      val y2   = data.slice(96, 128).toArray

      (for {
        p1 <- CurvePoint(x1, y1)
        p2 <- CurvePoint(x2, y2)
        affine = (p1 + p2).makeAffine()
        (x, y) = if (affine.isInfinity) (BigInt(0), BigInt(0)) else (affine.x, affine.y)
      } yield ByteVector(x.toByteArray).padLeft(32) ++ ByteVector(y.toByteArray).padLeft(32))
        .getOrElse(ByteVector.empty.padTo(32))
    }

    def gas(inputData: ByteVector): BigInt =
      500
  }

  object BN256Mul extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector = {
      val data = if (inputData.length < 96) inputData.padRight(96) else inputData
      val x    = data.slice(0, 32).toArray
      val y    = data.slice(32, 64).toArray
      val s    = data.slice(64, 96).toArray

      CurvePoint(x, y)
        .map { p =>
          val affine = (p * BigInt(1, s)).makeAffine()
          val (x, y) = if (affine.isInfinity) (BigInt(0), BigInt(0)) else (affine.x, affine.y)
          ByteVector(x.toByteArray).padLeft(32) ++ ByteVector(y.toByteArray).padLeft(32)
        }
        .getOrElse(ByteVector.empty.padTo(32))
    }

    def gas(inputData: ByteVector): BigInt =
      40000
  }

  object BN256Pairing extends PrecompiledContract {
    def exec(inputData: ByteVector): ByteVector = {
      if (inputData.length % 192 != 0) {
        println("not % 192 == 0")
        return ByteVector.empty.padTo(32)
      }

      val pairs = inputData.toArray.sliding(192, 192).toList.map { input =>
        val x = input.slice(0, 32)
        val y = input.slice(32, 64)

        val a = input.slice(64, 96)
        val b = input.slice(96, 128)
        val c = input.slice(128, 160)
        val d = input.slice(160, 192)

        for {
          g1 <- CurvePoint(x, y)
          g2 <- TwistPoint(a, b, c, d)
        } yield (g1, g2)
      }

      pairs
        .sequence[Option, (CurvePoint, TwistPoint)]
        .map(ps => ByteVector(if (BN256.pairingCheck(ps)) 1 else 0).padLeft(32))
        .getOrElse(ByteVector.empty.padTo(32))
    }

    def gas(inputData: ByteVector): BigInt =
      100000 + inputData.length / 192 * 80000
  }
}
