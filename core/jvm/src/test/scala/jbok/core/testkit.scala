package jbok.core

import java.net.InetSocketAddress

import cats.effect.IO
import cats.effect.concurrent.Ref
import jbok.common.testkit._
import jbok.core.config._
import jbok.core.messages._
import jbok.core.mining.{BlockMiner, TxGen}
import jbok.core.models._
import jbok.core.peer.{Peer, PeerUri}
import jbok.crypto._
import jbok.crypto.signature.{ECDSA, Signature}
import org.scalacheck._
import scodec.bits.ByteVector

object testkit {
  import CoreSpec._

  def fillConfigs(n: Int)(config: FullConfig): List[FullConfig] =
    List.fill(n)(config)

  def uint256Gen(min: UInt256 = UInt256.Zero, max: UInt256 = UInt256.MaxValue): Gen[UInt256] =
    for {
      value <- arbUint256.arbitrary
      mod = max - min
    } yield value.mod(mod) + min

  implicit def arbUint256 = Arbitrary {
    for {
      bytes <- genBoundedByteVector(32, 32)
    } yield UInt256(bytes)
  }

  implicit def arbAccount: Arbitrary[Account] = Arbitrary {
    for {
      nonce       <- arbUint256.arbitrary
      balance     <- arbUint256.arbitrary
      storageRoot <- genBoundedByteVector(32, 32)
      codeHash    <- genBoundedByteVector(32, 32)
    } yield Account(nonce, balance, storageRoot, codeHash)
  }

  implicit def arbAddress: Arbitrary[Address] = Arbitrary {
    for {
      bytes <- genBoundedByteVector(20, 20)
    } yield Address(bytes)
  }

  def genTx: Gen[Transaction] =
    for {
      nonce            <- bigIntGen
      gasPrice         <- bigIntGen
      gasLimit         <- bigIntGen
      receivingAddress <- Gen.option(arbAddress.arbitrary)
      value            <- bigIntGen
      payload          <- arbByteVector.arbitrary
    } yield Transaction(nonce, gasPrice, gasLimit, receivingAddress, value, payload)

  implicit def arbTransaction: Arbitrary[Transaction] = Arbitrary {
    genTx
  }

  implicit def arbSignedTransaction(implicit chainId: BigInt): Arbitrary[SignedTransaction] = Arbitrary {
    for {
      tx <- arbTransaction.arbitrary
      keyPair = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
      stx     = SignedTransaction.sign[IO](tx, keyPair).unsafeRunSync()
    } yield stx
  }

  implicit def arbBlockHeader: Arbitrary[BlockHeader] = Arbitrary {
    for {
      parentHash       <- genBoundedByteVector(32, 32)
      beneficiary      <- genBoundedByteVector(20, 20)
      stateRoot        <- genBoundedByteVector(32, 32)
      transactionsRoot <- genBoundedByteVector(32, 32)
      receiptsRoot     <- genBoundedByteVector(32, 32)
      logsBloom        <- genBoundedByteVector(256, 256)
      difficulty       <- bigIntGen
      number           <- bigIntGen
      gasLimit         <- bigIntGen
      gasUsed          <- bigIntGen
      unixTimestamp    <- Gen.chooseNum(0L, Long.MaxValue)
    } yield
      BlockHeader(
        parentHash = parentHash,
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
        extra = ByteVector.empty
      )
  }

  implicit def arbBlockBody(implicit chainId: BigInt): Arbitrary[BlockBody] = Arbitrary {
    for {
      stxs <- arbTxs.arbitrary
    } yield BlockBody(stxs)
  }

  implicit def arbSignedTransactions(implicit chainId: BigInt): Arbitrary[SignedTransactions] = Arbitrary {
    for {
      txs <- arbTxs.arbitrary
    } yield SignedTransactions(txs)
  }

  implicit def arbBlock(implicit chainId: BigInt): Arbitrary[Block] = Arbitrary {
    for {
      header <- arbBlockHeader.arbitrary
      body   <- arbBlockBody.arbitrary
    } yield Block(header, body)
  }

  implicit def arbReceipt: Arbitrary[Receipt] = Arbitrary {
    for {
      postTransactionStateHash <- genBoundedByteVector(32, 32)
      cumulativeGasUsed        <- bigIntGen
      logsBloomFilter          <- genBoundedByteVector(256, 256)
      txHash                   <- genBoundedByteVector(32, 32)
      gasUsed                  <- bigIntGen
    } yield
      Receipt(
        postTransactionStateHash = postTransactionStateHash,
        cumulativeGasUsed = cumulativeGasUsed,
        logsBloomFilter = logsBloomFilter,
        logs = Nil,
        txHash = txHash,
        contractAddress = None,
        gasUsed = gasUsed
      )
  }

  def genStatus(number: BigInt = 0, td: BigInt = 0)(implicit config: FullConfig): Gen[Status] =
    Gen.delay(Status(config.genesis.chainId, config.genesis.header.hash, number, td, ""))

  def genPeer(implicit config: FullConfig): Gen[Peer[IO]] =
    for {
      uri    <- arbPeerUri.arbitrary
      status <- genStatus()
    } yield Peer[IO](uri, status).unsafeRunSync()

  implicit def arbPeer(implicit config: FullConfig): Arbitrary[Peer[IO]] = Arbitrary {
    genPeer
  }

  def genPeers(min: Int, max: Int)(implicit config: FullConfig): Gen[List[Peer[IO]]] =
    for {
      size  <- Gen.chooseNum(min, max)
      peers <- Gen.listOfN(size, genPeer)
    } yield peers

  def genBlockHash: Gen[BlockHash] =
    for {
      hash   <- genBoundedByteVector(32, 32)
      number <- bigIntGen
    } yield BlockHash(hash, number)

  def genNewBlockHashes: Gen[NewBlockHashes] =
    for {
      hashes <- Gen.listOf(genBlockHash)
    } yield NewBlockHashes(hashes)

  implicit def arbNewBlockHashes: Arbitrary[NewBlockHashes] = Arbitrary(genNewBlockHashes)

  def genTxs(min: Int = 0, max: Int = 1024)(implicit config: FullConfig): Gen[List[SignedTransaction]] =
    for {
      size <- Gen.chooseNum(min, max)
      (_, txs) = TxGen.genTxs(size, Map(testKeyPair -> Account.empty())).unsafeRunSync()
    } yield txs

  implicit def arbTxs(implicit config: FullConfig): Arbitrary[List[SignedTransaction]] = Arbitrary {
    genTxs()
  }

  def genBlocks(min: Int, max: Int)(implicit config: FullConfig): Gen[List[Block]] = {
    val objects = locator.unsafeRunSync()
    val miner            = objects.get[BlockMiner[IO]]
    val status           = objects.get[Ref[IO, NodeStatus]]
    status.set(NodeStatus.Done).unsafeRunSync()
    for {
      size <- Gen.chooseNum(min, max)
      blocks = miner.stream.take(size.toLong).compile.toList.unsafeRunSync()
    } yield blocks.map(_.block)
  }

  def genBlock(parentOpt: Option[Block] = None, stxsOpt: Option[List[SignedTransaction]] = None)(implicit config: FullConfig): Gen[Block] = {
    val objects = locator.unsafeRunSync()
    val miner            = objects.get[BlockMiner[IO]]
    val status           = objects.get[Ref[IO, NodeStatus]]
    status.set(NodeStatus.Done).unsafeRunSync()
    val mined = miner.mine1(parentOpt, stxsOpt).unsafeRunSync()
    mined.right.get.block
  }

  implicit def arbPeerUri: Arbitrary[PeerUri] = Arbitrary {
    for {
      port <- Gen.chooseNum(10000, 65535)
    } yield PeerUri.fromTcpAddr(new InetSocketAddress("localhost", port))
  }
}
