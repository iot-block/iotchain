package jbok.core.validators

import cats.effect.IO
import cats.implicits._
import jbok.common.math.N
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
    gasPrice = N("30000000000"),
    gasLimit = 21000,
    receivingAddress = Address(hex"1e0cf4971f42462823b122a9a0a2206902b51132").some,
    value = N("1050230460000000000"),
    payload = ByteVector.empty
  )

  val stx = SignedTransaction.sign[IO](tx, randomKeyPair, chainId).unsafeRunSync()

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
      difficulty = N("14005986920576"),
      number = 3125369,
      gasLimit = 4699996,
      gasUsed = 84000,
      unixTimestamp = 1486131165,
      extra = hex"d5830104098650617269747986312e31332e30826c69"
    )

  val accumGasUsed = 0

  val upfrontGasCost: UInt256 = UInt256(senderBalance / 2)

  "TxValidator" should {
    "report as valid a tx from after EIP155" in {
      txValidator
        .validate(stx, senderAccount, header, upfrontGasCost, accumGasUsed)
        .attempt
        .unsafeRunSync() shouldBe Right(())
    }

    "report as invalid if a tx with error nonce" in {
      forAll { nonce: N =>
        val result = txValidator
          .validate(stx.copy(nonce = nonce), senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (nonce > TxValidator.maxNonceValue) result.left.get shouldBe a[TxSyntaxInvalid]
        else result.left.get shouldBe a[TxNonceInvalid]
      }
    }

    "report as syntactic invalid a tx with long gas price" in {
      forAll { gasPrice: N =>
        val result = txValidator
          .validate(stx.copy(gasPrice = gasPrice), senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (gasPrice > TxValidator.maxGasValue) result.left.get shouldBe a[TxSyntaxInvalid]
        else result shouldBe Right(())
      }
    }

    "report as syntactic invalid a tx with long value" in {
      forAll { value: N =>
        val result = txValidator
          .validate(stx.copy(value = value), senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (value > TxValidator.maxValue) result.left.get shouldBe a[TxSyntaxInvalid]
        else result shouldBe Right(())
      }
    }

    "report as syntactic invalid a tx with long r" in {
      forAll { r: N =>
        val result = txValidator
          .validate(stx.copy(r = r), senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()

        if (r >= TxValidator.secp256k1n) result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report a tx with invalid s as having invalid signature after homestead" in {
      forAll { s: N =>
        val result = txValidator
          .validate(stx.copy(s = s), senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (s < TxValidator.secp256k1n / 2 + 1 && s > 0) result shouldBe Right(())
        else result.left.get shouldBe a[TxSyntaxInvalid]
      }
    }

    "report as invalid if a tx with invalid nonce" in {
      forAll { nonce: N =>
        val result = txValidator
          .validate(stx.copy(nonce = nonce), senderAccount, header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (nonce == tx.nonce) result shouldBe Right(())
        else result.left.get shouldBe a[TxNonceInvalid]
      }
    }

    "report as invalid a tx with too low gas limit for intrinsic gas" in {
      forAll { gasLimit: N =>
        val result = txValidator
          .validate(stx.copy(gasLimit = gasLimit), senderAccount, header, upfrontGasCost, accumGasUsed)
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
      forAll { balance: UInt256 =>
        val result = txValidator
          .validate(stx, senderAccount.copy(balance = balance), header, upfrontGasCost, accumGasUsed)
          .attempt
          .unsafeRunSync()
        if (balance >= upfrontGasCost) result shouldBe Right(())
        else result.left.get shouldBe a[TxSenderCantPayUpfrontCostInvalid]
      }
    }
  }
}
