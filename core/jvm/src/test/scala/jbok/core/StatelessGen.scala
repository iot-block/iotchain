package jbok.core

import java.net.InetSocketAddress

import cats.effect.{Concurrent, IO}
import jbok.common.gen
import jbok.core.config.FullConfig
import jbok.core.ledger.History
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerUri}
import jbok.crypto.signature.{ECDSA, Signature}
import jbok.evm._
import jbok.persistent.MemoryKVStore
import org.scalacheck.Gen
import scodec.bits.ByteVector

object StatelessGen {
  // models
  val chainId: Gen[ChainId] =
    gen.N.map(n => ChainId(n))

  def uint256(min: UInt256 = UInt256.zero, max: UInt256 = UInt256.MaxValue): Gen[UInt256] =
    for {
      value <- gen.sizedByteVector(32).map(UInt256.apply)
      mod = max - min
    } yield min + value.mod(mod)

  val account: Gen[Account] =
    for {
      nonce       <- uint256()
      balance     <- uint256()
      storageRoot <- gen.sizedByteVector(32)
      codeHash    <- gen.sizedByteVector(32)
    } yield Account(nonce, balance, storageRoot, codeHash)

  val address: Gen[Address] =
    for {
      bytes <- gen.sizedByteVector(20)
    } yield Address(bytes)

  val transaction: Gen[Transaction] =
    for {
      nonce            <- gen.N
      gasPrice         <- gen.N
      gasLimit         <- gen.N
      receivingAddress <- Gen.option(address)
      value            <- gen.N
      payload          <- gen.byteVector
    } yield Transaction(nonce, gasPrice, gasLimit, receivingAddress, value, payload)

  val signedTransaction: Gen[SignedTransaction] =
    for {
      chainId <- chainId
      tx      <- transaction
      keyPair = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
      stx     = SignedTransaction.sign[IO](tx, keyPair, chainId).unsafeRunSync()
    } yield stx

  val blockHeader: Gen[BlockHeader] =
    for {
      parentHash       <- gen.sizedByteVector(32)
      beneficiary      <- gen.sizedByteVector(20)
      stateRoot        <- gen.sizedByteVector(32)
      transactionsRoot <- gen.sizedByteVector(32)
      receiptsRoot     <- gen.sizedByteVector(32)
      logsBloom        <- gen.sizedByteVector(256)
      difficulty       <- gen.N
      number           <- gen.N
      gasLimit         <- gen.N
      gasUsed          <- gen.N
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

  val blockBody: Gen[BlockBody] =
    for {
      stxs <- Gen.listOf(signedTransaction)
    } yield BlockBody(stxs)

  val block: Gen[Block] =
    for {
      header <- blockHeader
      body   <- blockBody
    } yield Block(header, body)

  val receipt: Gen[Receipt] =
    for {
      postTransactionStateHash <- gen.sizedByteVector(32)
      cumulativeGasUsed        <- gen.N
      logsBloomFilter          <- gen.sizedByteVector(256)
      txHash                   <- gen.sizedByteVector(32)
      gasUsed                  <- gen.N
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

  // messages
  val blockHash: Gen[BlockHash] =
    for {
      hash   <- gen.boundedByteVector(32, 32)
      number <- gen.N
    } yield BlockHash(hash, number)

  val newBlockHashes: Gen[NewBlockHashes] =
    for {
      hashes <- Gen.listOf(blockHash)
    } yield NewBlockHashes(hashes)

  val newBlock: Gen[NewBlock] =
    block.map(b => NewBlock(b))

  val signedTransactions: Gen[SignedTransactions] =
    Gen.listOf(signedTransaction).map(stxs => SignedTransactions(stxs))

  val peerUri: Gen[PeerUri] =
    for {
      port <- Gen.chooseNum(10000, 65535)
    } yield PeerUri.fromTcpAddr(new InetSocketAddress("localhost", port))

  def status(config: FullConfig): Gen[Status] =
    for {
      number <- gen.N
      td     <- gen.N
    } yield Status(config.genesis.chainId, config.genesis.header.hash, number, td, "")

  def peer(config: FullConfig)(implicit F: Concurrent[IO]): Gen[Peer[IO]] =
    for {
      uri    <- peerUri
      status <- status(config)
    } yield Peer[IO](uri, status).unsafeRunSync()

  // evm
  val ownerAddr = Address(0x123456)

  val callerAddr = Address(0xabcdef)

  val receiveAddr = Address(0x654321)

  val testStackMaxSize = 32

  val program: Gen[Program] =
    for {
      code <- gen.boundedByteVector(0, 256)
    } yield Program(code)

  val execEnv: Gen[ExecEnv] =
    for {
      gasPrice  <- uint256()
      inputData <- gen.boundedByteVector(0, 256)
      value     <- uint256()
      program   <- program
      header    <- blockHeader
      callDepth <- gen.int(0, 1024)
    } yield ExecEnv(ownerAddr, callerAddr, callerAddr, gasPrice, inputData, value, program, header, callDepth)

  def programContext(evmConfig: EvmConfig): Gen[ProgramContext[IO]] =
    for {
      env   <- execEnv
      gas   <- uint256(UInt256.MaxValue, UInt256.MaxValue)
      world <- worldState(env)
    } yield ProgramContext(env, receiveAddr, gas, world, evmConfig)

  def worldState(env: ExecEnv): Gen[WorldState[IO]] = {
    import CoreSpec.timer
    val history = History(MemoryKVStore[IO].unsafeRunSync(), CoreSpec.chainId)
    history
      .getWorldState()
      .unsafeRunSync()
      .putCode(env.ownerAddr, env.program.code)
      .putAccount(env.ownerAddr, Account.empty().increaseBalance(env.value))
      .persisted
      .unsafeRunSync()
  }

  val memory: Gen[Memory] =
    for {
      bv <- gen.boundedByteVector(0, 256)
    } yield Memory.empty.store(0, bv)

  def stack(nElems: Int, elemGen: Gen[UInt256]): Gen[Stack] =
    for {
      list <- Gen.listOfN(nElems, elemGen)
      stack = Stack.empty(testStackMaxSize)
    } yield stack.push(list)

  def programState(evmConfig: EvmConfig): Gen[ProgramState[IO]] =
    for {
      context <- programContext(evmConfig)
    } yield ProgramState(context)
}
