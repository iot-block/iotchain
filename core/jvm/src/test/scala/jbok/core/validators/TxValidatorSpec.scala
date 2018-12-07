package jbok.core.validators

import cats.effect.IO
import jbok.JbokSpec
import jbok.common.testkit._
import jbok.core.Fixtures
import jbok.core.config.Configs.BlockChainConfig
import jbok.core.models._
import jbok.core.validators.TxInvalid._
import jbok.crypto.signature.{ECDSA, Signature}
import scodec.bits._

class TxValidatorSpec extends JbokSpec {
  val keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()

  val tx = Transaction(
    nonce = 12345,
    gasPrice = BigInt("30000000000"),
    gasLimit = 21000,
    receivingAddress = Address(hex"1e0cf4971f42462823b122a9a0a2206902b51132"),
    value = BigInt("1050230460000000000"),
    payload = ByteVector.empty
  )

  val stx = SignedTransaction.sign(tx, keyPair, 0x3d.toByte)

  val senderBalance = 100

  val senderAccount = Account.empty(UInt256(tx.nonce)).copy(balance = senderBalance)

  val header = Fixtures.Blocks.Block3125369.header.copy(number = 3000020, gasLimit = 4710000)

  val accumGasUsed = 0 //Both are the first tx in the block

  val upfrontGasCost: UInt256 = UInt256(senderBalance / 2)

  val transactionValidator = new TxValidator[IO](BlockChainConfig())

  "TxValidator" should {
    "report as valid a tx from after EIP155" in {
      transactionValidator
        .validate(stx, senderAccount, header, upfrontGasCost, accumGasUsed)
        .attempt
        .unsafeRunSync() shouldBe Right(())
    }

    "report as invalid if a tx with error nonce" in {
      forAll(bigInt64Gen) { nonce =>
        val invalidSSignedTx = stx.copy(nonce = nonce)
        val result = transactionValidator
          .validate(invalidSSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas limit" in {
      forAll(bigInt64Gen) { nonce =>
        val invalidSSignedTx = stx.copy(nonce = nonce)
        val result = transactionValidator
          .validate(invalidSSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas price" in {
      forAll(bigInt64Gen) { gasPrice =>
        val invalidGasPriceTx = stx.copy(gasPrice = gasPrice)
        val result = transactionValidator
          .validate(invalidGasPriceTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long value" in {
      forAll(bigInt64Gen) { value =>
        val invalidValueTx = stx.copy(value = value)
        val result = transactionValidator
          .validate(invalidValueTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long s" in {
      forAll(bigInt64Gen) { s =>
        val invalidSTx = stx.copy(s = s)
        val result = transactionValidator
          .validate(invalidSTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long r" in {
      forAll(bigInt64Gen) { r =>
        val invalidRTx = stx.copy(r = r)
        val result = transactionValidator
          .validate(invalidRTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report a tx with invalid r as having invalid signature" in {
      forAll(bigIntGen) { r =>
        val invalidRSignedTx = stx.copy(r = r)
        val result = transactionValidator
          .validate(invalidRSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (r < transactionValidator.secp256k1n && r > 0) result shouldBe Right(())
        else result shouldBe Left(TxSignatureInvalid)
      }
    }

    "report a tx with invalid s as having invalid signature after homestead" in {
      forAll(bigIntGen) { s =>
        val invalidSSignedTx = stx.copy(s = s)
        val result = transactionValidator
          .validate(invalidSSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (s < transactionValidator.secp256k1n / 2 + 1 && s > 0) result shouldBe Right(())
        else result shouldBe Left(TxSignatureInvalid)
      }
    }

    "report as invalid if a tx with invalid nonce" in {
      forAll(bigIntGen) { nonce =>
        val invalidNonceSignedTx = stx.copy(nonce = nonce)
        val result = transactionValidator
          .validate(invalidNonceSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (nonce == tx.nonce) result shouldBe Right(())
        else result.left.get shouldBe a[TxNonceInvalid]
      }
    }

    "report as invalid a tx with too low gas limit for intrinsic gas" in {
      forAll(bigIntGen) { gasLimit =>
        val invalidGasLimitTx = stx.copy(gasLimit = gasLimit)
        val result = transactionValidator
          .validate(invalidGasLimitTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (gasLimit == tx.gasLimit) result shouldBe Right(())
        else if (gasLimit > tx.gasLimit)
          if (gasLimit + accumGasUsed <= upfrontGasCost) result shouldBe Right(())
          else result.left.get shouldBe a[TrxGasLimitTooBigInvalid]
        else result.left.get shouldBe a[TxNotEnoughGasForIntrinsicInvalid]
      }
    }

    "report as invalid a tx with upfront cost higher than the sender's balance" in {
      forAll(genBoundedByteVector(32, 32)) { balance =>
        val invalidBalanceAccount = senderAccount.copy(balance = UInt256(balance))
        val result = transactionValidator
          .validate(stx, invalidBalanceAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (UInt256(balance) >= upfrontGasCost) result shouldBe Right(())
        else result.left.get shouldBe a[TxSenderCantPayUpfrontCostInvalid]
      }
    }
  }
}
