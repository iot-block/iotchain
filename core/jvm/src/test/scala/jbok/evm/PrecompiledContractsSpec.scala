package jbok.evm

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.models.{Address, UInt256}
import jbok.crypto._
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.persistent.KeyValueDB
import jbok.testkit.VMGens
import scodec.bits._

class PrecompiledContractsSpec extends JbokSpec {

  def buildContext(recipient: Address, inputData: ByteVector, gas: UInt256 = 1000000): ProgramContext[IO] = {
    val origin = Address(0xcafebabe)
    val env    = ExecEnv(recipient, origin, origin, 1000, inputData, 0, Program(ByteVector.empty), null, 0)
    val db     = KeyValueDB.inMemory[IO].unsafeRunSync()
    val world  = WorldStateProxy.inMemory[IO](db).unsafeRunSync()
    ProgramContext(env, recipient, gas, world, EvmConfig.PostEIP161ConfigBuilder(None))
  }

  "ECDSARECOVER" in {
    val keyPair  = SecP256k1.generateKeyPair().unsafeRunSync()
    val bytesGen = VMGens.getByteVectorGen(1, 128)

    forAll(bytesGen) { bytes =>
      val hash             = bytes.kec256
      val validSig         = SecP256k1.sign(hash.toArray, keyPair).unsafeRunSync()
      val recoveredPub     = SecP256k1.recoverPublic(hash.toArray, validSig).get
      val recoveredAddress = recoveredPub.bytes.kec256.slice(12, 32).padLeft(32)
      val inputData        = hash ++ UInt256(validSig.v).bytes ++ UInt256(validSig.r).bytes ++ UInt256(validSig.s).bytes

      val context = buildContext(PrecompiledContracts.EcDsaRecAddr, inputData)
      val result  = VM.run(context).unsafeRunSync()
      result.returnData shouldEqual recoveredAddress

      val gasUsed = context.startGas - result.gasRemaining
      gasUsed shouldEqual 3000
    }

    // invalid input - recovery failed
    val invalidInput         = ByteVector(Array.fill[Byte](128)(0))
    val contextWithWrongData = buildContext(PrecompiledContracts.EcDsaRecAddr, invalidInput)
    val resultFailedRecovery = VM.run(contextWithWrongData).unsafeRunSync()
    resultFailedRecovery.returnData shouldEqual ByteVector.empty

    val gasUsedFailedRecover = contextWithWrongData.startGas - resultFailedRecovery.gasRemaining
    gasUsedFailedRecover shouldEqual 3000
  }

  "ECDSARECOVER_Malformed_Recovery_ID_V" in {
    val validAddress = hex"000000000000000000000000a94f5374fce5edbc8e2a8697c15331677e6ebf0b"

    val validH = hex"18c547e4f7b0f325ad1e56f57e26c745b09a3e503d86e00e5255ff7f715d3d1c"
    val validR = hex"73b1693892219d736caba55bdb67216e485557ea6b6af75f37096c9aa6a5a75f"
    val validS = hex"eeb940b1d03b21e36b0e47e79769f095fe2ab855bd91e3a38756b7d75a9c4549"

    val validV   = hex"000000000000000000000000000000000000000000000000000000000000001c"
    val invalidV = hex"000000000000000000000000000000000000000000000000000000000000f01c"

    val invalidInput = validH ++ invalidV ++ validR ++ validS
    val validInput   = validH ++ validV ++ validR ++ validS

    // Valid Input
    val context = buildContext(PrecompiledContracts.EcDsaRecAddr, validInput)
    val result  = VM.run(context).unsafeRunSync()
    result.returnData shouldEqual validAddress

    // InvalidInput
    val invalidContext = buildContext(PrecompiledContracts.EcDsaRecAddr, invalidInput)
    val invalidResult  = VM.run(invalidContext).unsafeRunSync()
    invalidResult.returnData shouldEqual ByteVector.empty
  }

  "SHA256" in {
    val bytesGen = VMGens.getByteVectorGen(0, 256)
    forAll(bytesGen) { bytes =>
      val context = buildContext(PrecompiledContracts.Sha256Addr, bytes)
      val result  = VM.run(context).unsafeRunSync()
      result.returnData shouldEqual bytes.sha256

      val gasUsed     = context.startGas - result.gasRemaining
      val expectedGas = 60 + 12 * wordsForBytes(bytes.size)
      gasUsed shouldEqual expectedGas
    }
  }

  "RIPEMD160" in {
    val bytesGen = VMGens.getByteVectorGen(0, 256)
    forAll(bytesGen) { bytes =>
      val context = buildContext(PrecompiledContracts.Rip160Addr, bytes)
      val result  = VM.run(context).unsafeRunSync()
      result.returnData shouldEqual bytes.ripemd160

      val gasUsed     = context.startGas - result.gasRemaining
      val expectedGas = 600 + 120 * wordsForBytes(bytes.size)
      gasUsed shouldEqual expectedGas
    }
  }

  "IDENTITY" in {
    val bytesGen = VMGens.getByteVectorGen(0, 256)
    forAll(bytesGen) { bytes =>
      val context = buildContext(PrecompiledContracts.IdAddr, bytes)
      val result  = VM.run(context).unsafeRunSync()
      result.returnData shouldEqual bytes

      val gasUsed     = context.startGas - result.gasRemaining
      val expectedGas = 15 + 3 * wordsForBytes(bytes.size)
      gasUsed shouldEqual expectedGas
    }
  }

}
