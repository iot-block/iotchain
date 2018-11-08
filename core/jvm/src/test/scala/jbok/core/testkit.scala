package jbok.core

import jbok.common.testkit._
import jbok.core.models._
import jbok.crypto.signature.{ECDSA, Signature}
import org.scalacheck.Arbitrary._
import org.scalacheck._

object testkit {
  implicit val arbUint256 = Arbitrary(arbLong.arbitrary.map(x => UInt256(x)))

  implicit val arbAccount: Arbitrary[Account] = Arbitrary {
    for {
      nonce       <- arbUint256.arbitrary
      balance     <- arbUint256.arbitrary
      storageRoot <- genBoundedByteVector(32, 32)
      codeHash    <- genBoundedByteVector(32, 32)
    } yield Account(nonce, balance, storageRoot, codeHash)
  }

  implicit val arbAddress: Arbitrary[Address] = Arbitrary {
    for {
      bytes <- genBoundedByteVector(20, 20)
    } yield Address(bytes)
  }

  implicit val arbTransaction: Arbitrary[Transaction] = Arbitrary {
    for {
      nonce            <- arbBigInt.arbitrary
      gasPrice         <- arbBigInt.arbitrary
      gasLimit         <- arbBigInt.arbitrary
      receivingAddress <- Gen.option(arbAddress.arbitrary)
      value            <- arbBigInt.arbitrary
      payload          <- arbByteVector.arbitrary
    } yield Transaction(nonce, gasPrice, gasLimit, receivingAddress, value, payload)
  }

  implicit val arbBlockHeader: Arbitrary[BlockHeader] = Arbitrary {
    for {
      parentHash       <- genBoundedByteVector(32, 32)
      ommersHash       <- genBoundedByteVector(32, 32)
      beneficiary      <- genBoundedByteVector(20, 20)
      stateRoot        <- genBoundedByteVector(32, 32)
      transactionsRoot <- genBoundedByteVector(32, 32)
      receiptsRoot     <- genBoundedByteVector(32, 32)
      logsBloom        <- genBoundedByteVector(50, 50)
      difficulty       <- arbBigInt.arbitrary
      number           <- arbBigInt.arbitrary
      gasLimit         <- arbBigInt.arbitrary
      gasUsed          <- arbBigInt.arbitrary
      unixTimestamp    <- arbLong.arbitrary
      extraData        <- arbByteVector.arbitrary
      mixHash          <- arbByteVector.arbitrary
      nonce            <- arbByteVector.arbitrary
    } yield
      BlockHeader(
        parentHash = parentHash,
        ommersHash = ommersHash,
        beneficiary = beneficiary,
        stateRoot = stateRoot,
        transactionsRoot = transactionsRoot,
        receiptsRoot = receiptsRoot,
        logsBloom = logsBloom,
        difficulty = difficulty,
        number = number,
        gasLimit = gasLimit,
        gasUsed = gasUsed,
        unixTimestamp = unixTimestamp,
        extraData = extraData,
        mixHash = mixHash,
        nonce = nonce
      )
  }

  implicit val arbSignedTxs: Arbitrary[List[SignedTransaction]] = Arbitrary {
    for {
      txs <- Gen.listOf(arbTransaction.arbitrary)
      keyPair = Signature[ECDSA].generateKeyPair().unsafeRunSync()
      stxs = txs.map(tx => SignedTransaction.sign(tx, keyPair))
    } yield stxs
  }

  implicit val arbBlock: Arbitrary[Block] = Arbitrary {
    for {
      header <- arbBlockHeader.arbitrary
      stxs <- arbSignedTxs.arbitrary
      td <- arbBigInt.arbitrary
    } yield Block(header, BlockBody(stxs, Nil))
  }

  implicit val arbReceipt: Arbitrary[Receipt] = Arbitrary {
    for {
      postTransactionStateHash <- genBoundedByteVector(32, 32)
      cumulativeGasUsed        <- arbBigInt.arbitrary
      logsBloomFilter          <- genBoundedByteVector(256, 256)
    } yield
      Receipt(
        postTransactionStateHash = postTransactionStateHash,
        cumulativeGasUsed = cumulativeGasUsed,
        logsBloomFilter = logsBloomFilter,
        logs = Nil
      )
  }
}
