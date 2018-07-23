package jbok.testkit

import java.math.BigInteger
import java.security.SecureRandom

import cats.effect.IO
import jbok.core.messages.NewBlock
import jbok.core.models._
import jbok.crypto.signature.Ed25519
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector

object Gens extends Gens
trait Gens {
  val byteGen: Gen[Byte] = Gen.choose(Byte.MinValue, Byte.MaxValue)

  val shortGen: Gen[Short] = Gen.choose(Short.MinValue, Short.MaxValue)

  def intGen(min: Int, max: Int): Gen[Int] = Gen.choose(min, max)

  val intGen: Gen[Int] = Gen.choose(Int.MinValue, Int.MaxValue)

  val longGen: Gen[Long] = Gen.choose(Long.MinValue, Long.MaxValue)

  val bigIntGen: Gen[BigInt] = byteArrayOfNItemsGen(32).map(b => new BigInteger(1, b))

  def randomSizeByteArrayGen(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen.choose(minSize, maxSize).flatMap(byteArrayOfNItemsGen(_))

  def byteArrayOfNItemsGen(n: Int): Gen[Array[Byte]] = Gen.listOfN(n, Arbitrary.arbitrary[Byte]).map(_.toArray)

  def randomSizeByteStringGen(minSize: Int, maxSize: Int): Gen[ByteVector] =
    Gen.choose(minSize, maxSize).flatMap(byteStringOfLengthNGen)

  def byteStringOfLengthNGen(n: Int): Gen[ByteVector] = byteArrayOfNItemsGen(n).map(ByteVector.apply)

  def listByteStringOfNItemsGen(n: Int): Gen[List[ByteVector]] = Gen.listOf(byteStringOfLengthNGen(n))

  def hexPrefixDecodeParametersGen(): Gen[(Array[Byte], Boolean)] =
    for {
      aByteList <- Gen.nonEmptyListOf(Arbitrary.arbitrary[Byte])
      t <- Arbitrary.arbitrary[Boolean]
    } yield (aByteList.toArray, t)

  def keyValueListGen(): Gen[List[(Int, Int)]] =
    for {
      aKeyList <- Gen.nonEmptyListOf(Arbitrary.arbitrary[Int]).map(_.distinct)
    } yield aKeyList.zip(aKeyList)

  def receiptGen: Gen[Receipt] =
    for {
      postTransactionStateHash <- byteArrayOfNItemsGen(32)
      cumulativeGasUsed <- bigIntGen
      logsBloomFilter <- byteArrayOfNItemsGen(256)
    } yield
      Receipt(
        postTransactionStateHash = ByteVector(postTransactionStateHash),
        cumulativeGasUsed = cumulativeGasUsed,
        logsBloomFilter = ByteVector(logsBloomFilter),
        logs = Nil
      )

  def transactionGen: Gen[Transaction] =
    for {
      nonce <- bigIntGen
      gasPrice <- bigIntGen
      gasLimit <- bigIntGen
      receivingAddress <- byteArrayOfNItemsGen(20).map(Address.apply)
      value <- bigIntGen
      payload <- byteStringOfLengthNGen(256)
    } yield
      Transaction(
        nonce,
        gasPrice,
        gasLimit,
        Some(receivingAddress),
        value,
        payload
      )

  def receiptsGen(n: Int): Gen[Seq[Seq[Receipt]]] = Gen.listOfN(n, Gen.listOf(receiptGen))

//  def branchNodeGen: Gen[BranchNode] = for {
//    children <- Gen.listOfN(16, byteStringOfLengthNGen(32)).map(childrenList => childrenList.map(child => Some(Left(child))))
//    terminator <- byteStringOfLengthNGen(32)
//  } yield BranchNode(children, Some(terminator))
//
//  def extensionNodeGen: Gen[ExtensionNode] = for {
//    keyNibbles <- byteArrayOfNItemsGen(32)
//    value <- byteStringOfLengthNGen(32)
//  } yield ExtensionNode(ByteString(bytesToNibbles(keyNibbles)), Left(value))
//
//  def leafNodeGen: Gen[LeafNode] = for {
//    keyNibbles <- byteArrayOfNItemsGen(32)
//    value <- byteStringOfLengthNGen(32)
//  } yield LeafNode(ByteString(bytesToNibbles(keyNibbles)), value)
//
//  def nodeGen: Gen[Node] = Gen.choose(0, 2).flatMap{ i =>
//    i match {
//      case 0 => branchNodeGen
//      case 1 => extensionNodeGen
//      case 2 => leafNodeGen
//    }
//  }

  def signedTxSeqGen(length: Int, secureRandom: SecureRandom, chainId: Option[Byte]): Gen[List[SignedTransaction]] = {
    val senderKeys = Ed25519.generateKeyPair[IO].unsafeRunSync()
    val txsSeqGen = Gen.listOfN(length, transactionGen)
    txsSeqGen.map { txs =>
      txs.map { tx =>
        SignedTransaction.sign(tx, senderKeys, chainId)
      }
    }
  }

  def newBlockGen(secureRandom: SecureRandom, chainId: Option[Byte]): Gen[NewBlock] =
    for {
      blockHeader <- blockHeaderGen
      stxs <- signedTxSeqGen(10, secureRandom, chainId)
      uncles <- listBlockHeaderGen
      td <- bigIntGen
    } yield NewBlock(Block(blockHeader, BlockBody(stxs, uncles)))

  def blockHeaderGen: Gen[BlockHeader] =
    for {
      parentHash <- byteStringOfLengthNGen(32)
      ommersHash <- byteStringOfLengthNGen(32)
      beneficiary <- byteStringOfLengthNGen(20)
      stateRoot <- byteStringOfLengthNGen(32)
      transactionsRoot <- byteStringOfLengthNGen(32)
      receiptsRoot <- byteStringOfLengthNGen(32)
      logsBloom <- byteStringOfLengthNGen(50)
      difficulty <- bigIntGen
      number <- bigIntGen
      gasLimit <- bigIntGen
      gasUsed <- bigIntGen
      unixTimestamp <- intGen.map(_.abs)
      extraData <- byteStringOfLengthNGen(8)
      mixHash <- byteStringOfLengthNGen(8)
      nonce <- byteStringOfLengthNGen(8)
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

  def listBlockHeaderGen: Gen[List[BlockHeader]] = Gen.listOf(blockHeaderGen)
}
