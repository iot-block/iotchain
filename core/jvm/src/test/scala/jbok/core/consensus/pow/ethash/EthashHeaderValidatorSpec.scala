package jbok.core.consensus.pow.ethash

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.config.Configs.{BlockChainConfig, DaoForkConfig}
import jbok.core.HistoryFixture
import jbok.core.consensus.pow.ethash.EthashHeaderInvalid._
import jbok.core.models._
import jbok.testkit.Gens
import scodec.bits._

class EthashHeaderValidatorFixture extends HistoryFixture {
  val validBlockHeader = BlockHeader(
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
    extraData = hex"d783010507846765746887676f312e372e33856c696e7578",
    mixHash = hex"6bc729364c9b682cfa923ba9480367ebdfa2a9bca2a652fe975e8d5958f696dd",
    nonce = hex"797a8f3a494f937b"
  )

  val validBlockParent = BlockHeader(
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
    extraData = hex"d783010507846765746887676f312e372e33856c696e7578",
    mixHash = hex"7f9ac1ddeafff0f926ed9887b8cf7d50c3f919d902e618b957022c46c8b404a6",
    nonce = hex"3fc7bc671f7cee70"
  )

  val blockChainConfig = BlockChainConfig()
  val daoForkConfig    = DaoForkConfig().copy(blockExtraData = Some(hex"d783010507846765746887676f312e372e33856c696e7578"))
  history.save(validBlockParent).unsafeRunSync()
  val blockHeaderValidator = new EthashHeaderValidator[IO](blockChainConfig, daoForkConfig)
}

class EthashHeaderValidatorSpec extends JbokSpec with Gens {
  "EthashHeaderValidator" should {
    "return a failure if created based on invalid extra data" in new EthashHeaderValidatorFixture {
      forAll(randomSizeByteStringGen(33, 32 * 2)) { invalidExtraData =>
        val invalidExtraDataHeader = validBlockHeader.copy(extraData = invalidExtraData)
        blockHeaderValidator.validate(validBlockParent, invalidExtraDataHeader).attempt.unsafeRunSync() shouldBe Left(
          HeaderExtraDataInvalid)
      }
    }

    "return a failure if created based on invalid difficulty" in new EthashHeaderValidatorFixture {
      forAll(bigIntGen) { difficulty =>
        val difficultyHeader = validBlockHeader.copy(difficulty = difficulty)
        val result           = blockHeaderValidator.validate(validBlockParent, difficultyHeader).attempt.unsafeRunSync()
        if (difficulty == validBlockHeader.difficulty) result shouldBe Right(difficultyHeader)
        else result shouldBe Left(HeaderDifficultyInvalid)
      }
    }

    "return a failure if created based on invalid nonce" in new EthashHeaderValidatorFixture {
      forAll(byteVectorOfLengthNGen(8)) { nonce =>
        val invalidNonce = validBlockHeader.copy(nonce = nonce)
        val result       = blockHeaderValidator.validate(validBlockParent, invalidNonce).attempt.unsafeRunSync()
        if (nonce.equals(validBlockHeader.nonce)) result shouldBe Right(invalidNonce)
        else result shouldBe Left(HeaderPoWInvalid)
      }
    }
  }
}

