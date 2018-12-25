package jbok.core.validators

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.ledger.History
import jbok.core.models.BlockHeader
import jbok.core.validators.HeaderInvalid._
import scodec.bits._
import jbok.common.testkit._
import jbok.core.testkit._

class HeaderValidatorSpec extends JbokSpec {
  implicit val config = testConfig

  val history = random[History[IO]]

  val validHeader = BlockHeader(
    parentHash = hex"d882d5c210bab4cb7ef0b9f3dc2130cb680959afcd9a8f9bf83ee6f13e2f9da3",
    ommersHash = hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
    beneficiary = hex"95f484419881c6e9b6de7fb3f8ad03763bd49a89",
    stateRoot = hex"634a2b20c9e02afdda7157afe384306c5acc4fb9c09b45dc0203c0fbb2fed0e6",
    transactionsRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
    receiptsRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
    logsBloom = ByteVector.fromValidHex("00" * 256),
    difficulty = BigInt("989772"),
    number = 20,
    gasLimit = 131620495,
    gasUsed = 0,
    unixTimestamp = 1486752441,
    extra = ByteVector.empty
//    extraData = hex"d783010507846765746887676f312e372e33856c696e7578",
//    mixHash = hex"6bc729364c9b682cfa923ba9480367ebdfa2a9bca2a652fe975e8d5958f696dd",
//    nonce = hex"797a8f3a494f937b"
  )

  val validParent = BlockHeader(
    parentHash = hex"677a5fb51d52321b03552e3c667f602cc489d15fc1d7824445aee6d94a9db2e7",
    ommersHash = hex"1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
    beneficiary = hex"95f484419881c6e9b6de7fb3f8ad03763bd49a89",
    stateRoot = hex"cddeeb071e2f69ad765406fb7c96c0cd42ddfc6ec54535822b564906f9e38e44",
    transactionsRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
    receiptsRoot = hex"56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
    logsBloom = ByteVector.fromValidHex("00" * 256),
    difficulty = BigInt("989289"),
    number = 19,
    gasLimit = 131749155,
    gasUsed = 0,
    unixTimestamp = 1486752440,
    extra = ByteVector.empty,
//    extraData = hex"d783010507846765746887676f312e372e33856c696e7578",
//    mixHash = hex"7f9ac1ddeafff0f926ed9887b8cf7d50c3f919d902e618b957022c46c8b404a6",
//    nonce = hex"3fc7bc671f7cee70"
  )

  history.putBlockHeader(validParent).unsafeRunSync()

  "HeaderValidator" should {
    "validate correctly formed BlockHeaders" in {
      HeaderValidator.preExecValidate[IO](validParent, validHeader).unsafeRunSync()
    }

    "return a failure if created based on invalid gas used" in {
      forAll(bigIntGen) { gasUsed =>
        val gasUsedHeader = validHeader.copy(gasUsed = gasUsed)
        val result        = HeaderValidator.preExecValidate[IO](validParent, gasUsedHeader).attempt.unsafeRunSync()
        if (gasUsed <= validHeader.gasLimit) result shouldBe Right(gasUsedHeader)
        else result shouldBe Left(HeaderGasUsedInvalid)
      }
    }

    "return a failure if created based on invalid gas limit" in {
      val lowerGasLimit = BigInt(5000).max(validParent.gasLimit - validParent.gasLimit / 1024 + 1)
      val upperGasLimit = validParent.gasLimit + validParent.gasLimit / 1024 - 1
      forAll(bigIntGen) { gasLimit =>
        val gasLimitHeader = validHeader.copy(gasLimit = gasLimit)
        val result         = HeaderValidator.preExecValidate[IO](validParent, gasLimitHeader).attempt.unsafeRunSync()
        if (gasLimit <= upperGasLimit && gasLimit >= lowerGasLimit) result shouldBe Right(gasLimitHeader)
        else result.left.get shouldBe a[HeaderGasLimitInvalid]
      }
    }

    "return a failure if created based on invalid number" in {
      forAll(bigIntGen) { number =>
        val invalidNumberHeader = validHeader.copy(number = number)
        val result              = HeaderValidator.preExecValidate[IO](validParent, invalidNumberHeader).attempt.unsafeRunSync()
        if (number == validHeader.number + 1) result shouldBe Right(invalidNumberHeader)
        else result shouldBe Left(HeaderNumberInvalid)
      }
    }

    "return a failure if the parent's header is not in storage" in {
      HeaderValidator.preExecValidate[IO](IO.pure(None), validHeader).attempt.unsafeRunSync() shouldBe Left(
        HeaderParentNotFoundInvalid)
    }

    "return a failure if created based on invalid receipts header" in {
//      forAll(genBoundedByteVector(0, 32)) { receiptesHash =>
//        val invalidReceiptsHash = validBlockHeader.copy(receiptsRoot = receiptesHash)
//        val result = blockValidator
//          .validate(Block(invalidReceiptsHash, validBlockBody), validReceipts)
//          .attempt
//          .unsafeRunSync()
//        if (receiptesHash == validBlockHeader.receiptsRoot) result shouldBe Right(())
//        else result shouldBe Left(BlockReceiptsHashInvalid)
//      }
    }

    "return a failure if created based on invalid log bloom header" in {
//      forAll(genBoundedByteVector(0, 32)) { logBloom =>
//        val invalidLogBloom = validBlockHeader.copy(logsBloom = logBloom)
//        val result = blockValidator
//          .validate(Block(invalidLogBloom, validBlockBody), validReceipts)
//          .attempt
//          .unsafeRunSync()
//        if (logBloom == validBlockHeader.logsBloom) result shouldBe Right(())
//        else result shouldBe Left(BlockLogBloomInvalid)
//      }
    }

    "return a failure if a receiptes is not valid due to wrong receipts hash" in {
//      blockValidator
//        .validateBlockAndReceipts(validBlockHeader, validReceipts.reverse)
//        .attempt
//        .unsafeRunSync() shouldBe Left(BlockReceiptsHashInvalid)
    }
  }
}
