package jbok.core.validators

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.config.Configs.BlockChainConfig
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

    "one case" in new TransactionValidatorFixture {
      val payload = ByteVector.fromValidHex(
        "60806040526040805190810160405280600481526020017f48302e31000000000000000000000000000000000000000000000000000000008152506006908051906020019062000051929190620001e3565b506000600760006101000a81548160ff02191690831515021790555033600760016101000a81548173ffffffffffffffffffffffffffffffffffffffff021916908373ffffffffffffffffffffffffffffffffffffffff160217905550348015620000bb57600080fd5b506000806040805190810160405280600981526020017f41657465726e697479000000000000000000000000000000000000000000000081525060126040805190810160405280600281526020017f414500000000000000000000000000000000000000000000000000000000000081525083600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000208190555083600081905550826003908051906020019062000190929190620001e3565b5081600460006101000a81548160ff021916908360ff1602179055508060059080519060200190620001c4929190620001e3565b505050505060029050806301e133800242016008819055505062000292565b828054600181600116156101000203166002900490600052602060002090601f016020900481019282601f106200022657805160ff191683800117855562000257565b8280016001018555821562000257579182015b828111156200025657825182559160200191906001019062000239565b5b5090506200026691906200026a565b5090565b6200028f91905b808211156200028b57600081600090555060010162000271565b5090565b90565b61153b80620002a26000396000f3006080604052600436106100e6576000357c0100000000000000000000000000000000000000000000000000000000900463ffffffff16806301339c21146100f857806302d05d3f1461010f57806306fdde0314610166578063095ea7b3146101f657806318160ddd1461025b57806323b872dd14610286578063313ce5671461030b57806354fd4d501461033c57806370a08231146103cc57806373d08bc51461042357806395d89b41146104cc578063a9059cbb1461055c578063cae9ca51146105c1578063d9a6cf811461066c578063dd62ed3e14610697578063e77a912f1461070e575b3480156100f257600080fd5b50600080fd5b34801561010457600080fd5b5061010d61073d565b005b34801561011b57600080fd5b506101246107cf565b604051808273ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200191505060405180910390f35b34801561017257600080fd5b5061017b6107f5565b6040518080602001828103825283818151815260200191508051906020019080838360005b838110156101bb5780820151818401526020810190506101a0565b50505050905090810190601f1680156101e85780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b34801561020257600080fd5b50610241600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919080359060200190929190505050610893565b604051808215151515815260200191505060405180910390f35b34801561026757600080fd5b506102706108bf565b6040518082815260200191505060405180910390f35b34801561029257600080fd5b506102f1600480360381019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190803573ffffffffffffffffffffffffffffffffffffffff169060200190929190803590602001909291905050506108c5565b604051808215151515815260200191505060405180910390f35b34801561031757600080fd5b506103206108e9565b604051808260ff1660ff16815260200191505060405180910390f35b34801561034857600080fd5b506103516108fc565b6040518080602001828103825283818151815260200191508051906020019080838360005b83811015610391578082015181840152602081019050610376565b50505050905090810190601f1680156103be5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b3480156103d857600080fd5b5061040d600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919050505061099a565b6040518082815260200191505060405180910390f35b34801561042f57600080fd5b506104ca60048036038101908080359060200190820180359060200190808060200260200160405190810160405280939291908181526020018383602002808284378201915050505050509192919290803590602001908201803590602001908080602002602001604051908101604052809392919081815260200183836020028082843782019150505050505091929192905050506109e3565b005b3480156104d857600080fd5b506104e1610be7565b6040518080602001828103825283818151815260200191508051906020019080838360005b83811015610521578082015181840152602081019050610506565b50505050905090810190601f16801561054e5780820380516001836020036101000a031916815260200191505b509250505060405180910390f35b34801561056857600080fd5b506105a7600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919080359060200190929190505050610c85565b604051808215151515815260200191505060405180910390f35b3480156105cd57600080fd5b50610652600480360381019080803573ffffffffffffffffffffffffffffffffffffffff16906020019092919080359060200190929190803590602001908201803590602001908080601f0160208091040260200160405190810160405280939291908181526020018383808284378201915050505050509192919290505050610ca7565b604051808215151515815260200191505060405180910390f35b34801561067857600080fd5b50610681610f44565b6040518082815260200191505060405180910390f35b3480156106a357600080fd5b506106f8600480360381019080803573ffffffffffffffffffffffffffffffffffffffff169060200190929190803573ffffffffffffffffffffffffffffffffffffffff169060200190929190505050610f4a565b6040518082815260200191505060405180910390f35b34801561071a57600080fd5b50610723610fd1565b604051808215151515815260200191505060405180910390f35b600760009054906101000a900460ff1615151561075657fe5b600760019054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff161415156107b257600080fd5b6001600760006101000a81548160ff021916908315150217905550565b600760019054906101000a900473ffffffffffffffffffffffffffffffffffffffff1681565b60038054600181600116156101000203166002900480601f01602080910402602001604051908101604052809291908181526020018280546001816001161561010002031660029004801561088b5780601f106108605761010080835404028352916020019161088b565b820191906000526020600020905b81548152906001019060200180831161086e57829003601f168201915b505050505081565b6000600760009054906101000a900460ff1615156108ad57fe5b6108b78383610fe4565b905092915050565b60005481565b600060085442111515156108d557fe5b6108e08484846110d6565b90509392505050565b600460009054906101000a900460ff1681565b60068054600181600116156101000203166002900480601f0160208091040260200160405190810160405280929190818152602001828054600181600116156101000203166002900480156109925780601f1061096757610100808354040283529160200191610992565b820191906000526020600020905b81548152906001019060200180831161097557829003601f168201915b505050505081565b6000600160008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020549050919050565b600080600080600760009054906101000a900460ff16151515610a0257fe5b600760019054906101000a900473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff16141515610a5e57600080fd5b6000549350600092505b8551831015610bd8578583815181101515610a7f57fe5b9060200190602002015191508483815181101515610a9957fe5b90602001906020020151905080600160008473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054141515610bcb57600160008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020548403935080600160008473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000208190555080840193508173ffffffffffffffffffffffffffffffffffffffff1660007fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef836040518082815260200191505060405180910390a35b8280600101935050610a68565b83600081905550505050505050565b60058054600181600116156101000203166002900480601f016020809104026020016040519081016040528092919081815260200182805460018160011615610100020316600290048015610c7d5780601f10610c5257610100808354040283529160200191610c7d565b820191906000526020600020905b815481529060010190602001808311610c6057829003601f168201915b505050505081565b60006008544211151515610c9557fe5b610c9f8383611104565b905092915050565b600082600260003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060008673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020819055508373ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925856040518082815260200191505060405180910390a38373ffffffffffffffffffffffffffffffffffffffff1660405180807f72656365697665417070726f76616c28616464726573732c75696e743235362c81526020017f616464726573732c627974657329000000000000000000000000000000000000815250602e01905060405180910390207c01000000000000000000000000000000000000000000000000000000009004338530866040518563ffffffff167c0100000000000000000000000000000000000000000000000000000000028152600401808573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020018481526020018373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001828051906020019080838360005b83811015610ee8578082015181840152602081019050610ecd565b50505050905090810190601f168015610f155780820380516001836020036101000a031916815260200191505b509450505050506000604051808303816000875af1925050501515610f3957600080fd5b600190509392505050565b60085481565b6000600260008473ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060008373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054905092915050565b600760009054906101000a900460ff1681565b600081600260003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060008573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167f8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925846040518082815260200191505060405180910390a36001905092915050565b6000600760009054906101000a900460ff1615156110f057fe5b6110fb848484611130565b90509392505050565b6000600760009054906101000a900460ff16151561111e57fe5b61112883836113a9565b905092915050565b600081600160008673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054101580156111fd575081600260008673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000205410155b801561120a575060008210155b151561121557600080fd5b81600160008573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000206000828254019250508190555081600160008673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000206000828254039250508190555081600260008673ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002060003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020600082825403925050819055508273ffffffffffffffffffffffffffffffffffffffff168473ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef846040518082815260200191505060405180910390a3600190509392505050565b600081600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff16815260200190815260200160002054101580156113fb575060008210155b151561140657600080fd5b81600160003373ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff1681526020019081526020016000206000828254039250508190555081600160008573ffffffffffffffffffffffffffffffffffffffff1673ffffffffffffffffffffffffffffffffffffffff168152602001908152602001600020600082825401925050819055508273ffffffffffffffffffffffffffffffffffffffff163373ffffffffffffffffffffffffffffffffffffffff167fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef846040518082815260200191505060405180910390a360019050929150505600a165627a7a723058200312954dbed0c90ff9aabb5de1525ba1d98faa56123086ef482e11d2da6a910e0029")
      val stx = new SignedTransaction(
        0,
        1,
        500000,
        Address(ByteVector.fromValidHex("0000000000000000000000000000000000000000")),
        BigInt("100000000000000000"),
        payload,
        BigInt("27"),
        BigInt("31310990506217099208932954782958555921685885244813926403802990284197485621990"),
        BigInt("11951324286620695803989283529696261827898752290840477818728663993836736052201")
      )
      val account = new Account(balance = UInt256(BigInt("100000000000000000")))
      val header = BlockHeader(
        hex"61d7b9cc0cee35e9c41c5529099dccb6dd36bde3def3771333266574a7983f6e",
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        ByteVector.empty,
        2,
        13,
        16930129,
        0,
        1540280384697L,
        hex"0000000000000000000000000000000000000000000000000000000000000000599111f59ccdb0c2ced37b8487c268f57b1b2344cc410706ae1022a5798b08b90470bb62220270a84ab8c94431858184101391c4191b47b98c0af69d4381e4665696a3a2a725614abfa5b940cb0296ade9f3542d1b",
        ByteVector.empty,
        hex"0000000000000000"
      )
      val upGasCost  = UInt256(BigInt("100000000000000000"))
      val accGasUsed = BigInt(0)
      val result = transactionValidator
        .validate(stx, account, header, upGasCost, accumGasUsed)
        .attempt
        .unsafeRunSync()
      result shouldBe Right(())
    }
  }
}
