package jbok.core.validators

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.Configs.BlockChainConfig
import jbok.core.models._
import jbok.core.validators.TransactionInvalid._
import jbok.core.{Fixtures, HistoryFixture}
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.testkit.Gens
import scodec.bits._

class TransactionValidatorFixture extends HistoryFixture {
  val keyPair = SecP256k1.generateKeyPair().unsafeRunSync()
  val txBeforeHomestead = Transaction(
    nonce = 81,
    gasPrice = BigInt("60000000000"),
    gasLimit = 21000,
    receivingAddress = Address(hex"32be343b94f860124dc4fee278fdcbd38c102d88"),
    value = BigInt("1143962220000000000"),
    payload = ByteVector.empty
  )
  val signedTxBeforeHomestead = SignedTransaction.sign(txBeforeHomestead, keyPair, None)

  //From block 0xdc7874d8ea90b63aa0ba122055e514db8bb75c0e7d51a448abd12a31ca3370cf with number 1200003 (tx index 0)
  val txAfterHomestead = Transaction(
    nonce = 1631,
    gasPrice = BigInt("30000000000"),
    gasLimit = 21000,
    receivingAddress = Address(hex"1e0cf4971f42462823b122a9a0a2206902b51132"),
    value = BigInt("1050230460000000000"),
    payload = ByteVector.empty
  )
  val signedTxAfterHomestead = SignedTransaction.sign(txAfterHomestead, keyPair, None)

  val txAfterEIP155 = Transaction(
    nonce = 12345,
    gasPrice = BigInt("30000000000"),
    gasLimit = 21000,
    receivingAddress = Address(hex"1e0cf4971f42462823b122a9a0a2206902b51132"),
    value = BigInt("1050230460000000000"),
    payload = ByteVector.empty
  )
  val signedTxAfterEIP155 = SignedTransaction.sign(txAfterEIP155, keyPair, Some(0x3d.toByte))

  val senderBalance = 100

  val senderAccountBeforeHomestead = Account.empty(UInt256(txBeforeHomestead.nonce)).copy(balance = senderBalance)

  val senderAccountAfterHomestead = Account.empty(UInt256(txAfterHomestead.nonce)).copy(balance = senderBalance)

  val senderAccountAfterEIP155 = Account.empty(UInt256(txAfterEIP155.nonce)).copy(balance = senderBalance)

  val blockHeaderBeforeHomestead = Fixtures.Blocks.Block3125369.header.copy(number = 1100000, gasLimit = 4700000)

  val blockHeaderAfterHomestead = Fixtures.Blocks.Block3125369.header.copy(number = 1200003, gasLimit = 4710000)

  val blockHeaderAfterEIP155 = Fixtures.Blocks.Block3125369.header.copy(number = 3000020, gasLimit = 4710000)

  val accumGasUsed = 0 //Both are the first tx in the block

  val upfrontGasCost: UInt256 = UInt256(senderBalance / 2)

  val transactionValidator = new TransactionValidator[IO](BlockChainConfig())
}

class TransactionValidatorSpec extends JbokSpec with Gens {
  "TxValidator" should {
    "report as valid a tx from before homestead" in new TransactionValidatorFixture {
      transactionValidator
        .validate(signedTxBeforeHomestead,
                  senderAccountBeforeHomestead,
                  blockHeaderBeforeHomestead,
                  upfrontGasCost,
                  accumGasUsed)
        .attempt
        .unsafeRunSync() shouldBe Right(())
    }

    "report as valid a tx from after homestead" in new TransactionValidatorFixture {
      transactionValidator
        .validate(signedTxAfterHomestead,
                  senderAccountAfterHomestead,
                  blockHeaderAfterHomestead,
                  upfrontGasCost,
                  accumGasUsed)
        .attempt
        .unsafeRunSync() shouldBe Right(())
    }

    "report as valid a tx from after EIP155" in new TransactionValidatorFixture {
      transactionValidator
        .validate(signedTxAfterEIP155, senderAccountAfterEIP155, blockHeaderAfterEIP155, upfrontGasCost, accumGasUsed)
        .attempt
        .unsafeRunSync() shouldBe Right(())
    }

    "report as invalid if a tx with error nonce" in new TransactionValidatorFixture {
      forAll(bigInt64Gen) { nonce =>
        val invalidSSignedTx = signedTxBeforeHomestead.copy(nonce = nonce)
        val result = transactionValidator
          .validate(invalidSSignedTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TransactionSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas limit" in new TransactionValidatorFixture {
      forAll(bigInt64Gen) { nonce =>
        val invalidSSignedTx = signedTxBeforeHomestead.copy(nonce = nonce)
        val result = transactionValidator
          .validate(invalidSSignedTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TransactionSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas price" in new TransactionValidatorFixture {
      forAll(bigInt64Gen) { gasPrice =>
        val invalidGasPriceTx = signedTxBeforeHomestead.copy(gasPrice = gasPrice)
        val result = transactionValidator
          .validate(invalidGasPriceTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TransactionSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long value" in new TransactionValidatorFixture {
      forAll(bigInt64Gen) { value =>
        val invalidValueTx = signedTxBeforeHomestead.copy(value = value)
        val result = transactionValidator
          .validate(invalidValueTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TransactionSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long s" in new TransactionValidatorFixture {
      forAll(bigInt64Gen) { s =>
        val invalidSTx = signedTxBeforeHomestead.copy(s = s)
        val result = transactionValidator
          .validate(invalidSTx, senderAccountBeforeHomestead, blockHeaderBeforeHomestead, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TransactionSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long r" in new TransactionValidatorFixture {
      forAll(bigInt64Gen) { r =>
        val invalidRTx = signedTxBeforeHomestead.copy(r = r)
        val result = transactionValidator
          .validate(invalidRTx, senderAccountBeforeHomestead, blockHeaderBeforeHomestead, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TransactionSyntaxInvalid]
      }
    }

    "report a tx with invalid r as having invalid signature" in new TransactionValidatorFixture {
      forAll(bigIntGen) { r =>
        val invalidRSignedTx = signedTxBeforeHomestead.copy(r = r)
        val result = transactionValidator
          .validate(invalidRSignedTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (r < transactionValidator.secp256k1n && r > 0) result shouldBe Right(())
        else result shouldBe Left(TransactionSignatureInvalid)
      }
    }

    "report a tx with invalid s as having invalid signature before homestead" in new TransactionValidatorFixture {
      forAll(bigIntGen) { s =>
        val invalidSSignedTx = signedTxBeforeHomestead.copy(s = s)
        val result = transactionValidator
          .validate(invalidSSignedTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (s < transactionValidator.secp256k1n && s > 0) result shouldBe Right(())
        else result shouldBe Left(TransactionSignatureInvalid)
      }
    }

    "report a tx with invalid s as having invalid signature after homestead" in new TransactionValidatorFixture {
      forAll(bigIntGen) { s =>
        val invalidSSignedTx = signedTxAfterHomestead.copy(s = s)
        val result = transactionValidator
          .validate(invalidSSignedTx,
                    senderAccountAfterHomestead,
                    blockHeaderAfterHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (s < transactionValidator.secp256k1n / 2 + 1 && s > 0) result shouldBe Right(())
        else result shouldBe Left(TransactionSignatureInvalid)
      }
    }

    "report as invalid if a tx with invalid nonce" in new TransactionValidatorFixture {
      forAll(bigIntGen) { nonce =>
        val invalidNonceSignedTx = signedTxBeforeHomestead.copy(nonce = nonce)
        val result = transactionValidator
          .validate(invalidNonceSignedTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (nonce == txBeforeHomestead.nonce) result shouldBe Right(())
        else result.left.get shouldBe a[TransactionNonceInvalid]
      }
    }

    "report as invalid a tx with too low gas limit for intrinsic gas" in new TransactionValidatorFixture {
      forAll(bigIntGen) { gasLimit =>
        val invalidGasLimitTx = signedTxBeforeHomestead.copy(gasLimit = gasLimit)
        val result = transactionValidator
          .validate(invalidGasLimitTx,
                    senderAccountBeforeHomestead,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (gasLimit == txBeforeHomestead.gasLimit) result shouldBe Right(())
        else if (gasLimit > txBeforeHomestead.gasLimit)
          if (gasLimit + accumGasUsed <= upfrontGasCost) result shouldBe Right(())
          else result.left.get shouldBe a[TransactionGasLimitTooBigInvalid]
        else result.left.get shouldBe a[TransactionNotEnoughGasForIntrinsicInvalid]
      }
    }

    "report as invalid a tx with upfront cost higher than the sender's balance" in new TransactionValidatorFixture {
      forAll(byteVectorOfLengthNGen(32)) { balance =>
        val invalidBalanceAccount = senderAccountBeforeHomestead.copy(balance = UInt256(balance))
        val result = transactionValidator
          .validate(signedTxBeforeHomestead,
                    invalidBalanceAccount,
                    blockHeaderBeforeHomestead,
                    upfrontGasCost,
                    accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (UInt256(balance) >= upfrontGasCost) result shouldBe Right(())
        else result.left.get shouldBe a[TransactionSenderCantPayUpfrontCostInvalid]
      }
    }
  }
}
