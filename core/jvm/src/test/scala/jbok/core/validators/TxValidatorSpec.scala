package jbok.core.validators

import cats.effect.IO
import cats.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.models._
import jbok.core.validators.TxInvalid._
import jbok.crypto.signature.{ECDSA, Signature}
import scodec.bits._

class TxValidatorSpec extends CoreSpec {
  val txValidator = locator.unsafeRunSync().get[TxValidator[IO]]

  val randomKeyPair = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()

  val tx = Transaction(
    nonce = 12345,
    gasPrice = BigInt("30000000000"),
    gasLimit = 21000,
    receivingAddress = Address(hex"1e0cf4971f42462823b122a9a0a2206902b51132").some,
    value = BigInt("1050230460000000000"),
    payload = ByteVector.empty
  )

  val stx = SignedTransaction.sign[IO](tx, randomKeyPair).unsafeRunSync()

  val senderBalance = 100

  val senderAccount = Account.empty(UInt256(tx.nonce)).copy(balance = senderBalance)

  val header: BlockHeader =
    BlockHeader(
      parentHash = hex"8345d132564b3660aa5f27c9415310634b50dbc92579c65a0825d9a255227a71",
      beneficiary = hex"df7d7e053933b5cc24372f878c90e62dadad5d42",
      stateRoot = hex"087f96537eba43885ab563227262580b27fc5e6516db79a6fc4d3bcd241dda67",
      transactionsRoot = hex"8ae451039a8bf403b899dcd23252d94761ddd23b88c769d9b7996546edc47fac",
      receiptsRoot = hex"8b472d8d4d39bae6a5570c2a42276ed2d6a56ac51a1a356d5b17c5564d01fd5d",
      logsBloom = ByteVector.fromValidHex("0" * 512),
      difficulty = BigInt("14005986920576"),
      number = 3125369,
      gasLimit = 4699996,
      gasUsed = 84000,
      unixTimestamp = 1486131165,
      extra = hex"d5830104098650617269747986312e31332e30826c69",
    )
//  Fixtures.Blocks.Block3125369.header.copy(number = 3000020, gasLimit = 4710000)

  val accumGasUsed = 0 //Both are the first tx in the block

  val upfrontGasCost: UInt256 = UInt256(senderBalance / 2)

  "TxValidator" should {
    "report as valid a tx from after EIP155" in {
      txValidator
        .validate(stx, senderAccount, header, upfrontGasCost, accumGasUsed)
        .attempt
        .unsafeRunSync() shouldBe Right(())
    }

    "report as invalid if a tx with error nonce" in {
      forAll(bigInt64Gen) { nonce =>
        val invalidSSignedTx = stx.copy(nonce = nonce)
        val result = txValidator
          .validate(invalidSSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas limit" in {
      forAll(bigInt64Gen) { nonce =>
        val invalidSSignedTx = stx.copy(nonce = nonce)
        val result = txValidator
          .validate(invalidSSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas price" in {
      forAll(bigInt64Gen) { gasPrice =>
        val invalidGasPriceTx = stx.copy(gasPrice = gasPrice)
        val result = txValidator
          .validate(invalidGasPriceTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long value" in {
      forAll(bigInt64Gen) { value =>
        val invalidValueTx = stx.copy(value = value)
        val result = txValidator
          .validate(invalidValueTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as syntactic invalid a tx with long s" in {
      forAll(bigInt64Gen) { s =>
        val invalidSTx = stx.copy(s = s)
        val result = txValidator
          .validate(invalidSTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe TxSignatureInvalid
      }
    }

    "report as syntactic invalid a tx with long r" in {
      forAll(bigInt64Gen) { r =>
        val invalidRTx = stx.copy(r = r)
        val result = txValidator
          .validate(invalidRTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        result.left.get shouldBe TxSignatureInvalid
      }
    }

    "report a tx with invalid s as having invalid signature after homestead" in {
      forAll(bigIntGen) { s =>
        val invalidSSignedTx = stx.copy(s = s)
        val result = txValidator
          .validate(invalidSSignedTx, senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (s < TxValidator.secp256k1n / 2 + 1 && s > 0) result shouldBe Right(())
        else result shouldBe Left(TxSignatureInvalid)
      }
    }

    "report as invalid if a tx with invalid nonce" in {
      forAll(bigIntGen) { nonce =>
        val invalidNonceSignedTx = stx.copy(nonce = nonce)
        val result = txValidator
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
        val result = txValidator
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
        val result = txValidator
          .validate(stx, invalidBalanceAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (UInt256(balance) >= upfrontGasCost) result shouldBe Right(())
        else result.left.get shouldBe a[TxSenderCantPayUpfrontCostInvalid]
      }
    }
  }
}
