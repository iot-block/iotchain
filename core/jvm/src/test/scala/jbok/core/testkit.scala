package jbok.core

import cats.effect.IO
import cats.effect.concurrent.{Deferred, Ref}
import com.typesafe.config.Config
import jbok.common.execution._
import jbok.common.testkit._
import jbok.core.config.Configs._
import jbok.core.config.{ConfigLoader, GenesisConfig, TypeSafeConfigHelper}
import jbok.core.consensus.Consensus
import jbok.core.consensus.istanbul.Istanbul.IstanbulExtra
import jbok.core.consensus.istanbul.{Istanbul, Snapshot, _}
import jbok.core.consensus.poa.clique.{Clique, CliqueConsensus}
import jbok.core.ledger.{BlockExecutor, History}
import jbok.core.messages._
import jbok.core.mining.{BlockMiner, SimAccount, TxGenerator}
import jbok.core.models._
import jbok.core.peer.discovery.{Discovery, PeerTable}
import jbok.core.peer.{Peer, PeerManager, PeerManagerPlatform, PeerNode, PeerStorePlatform, PeerType}
import jbok.core.pool.{BlockPool, BlockPoolConfig, OmmerPool, TxPool}
import jbok.core.sync._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}
import jbok.crypto.testkit._
import jbok.network.transport.UdpTransport
import jbok.persistent.testkit._
import jbok.persistent.{CacheBuilder, KeyValueDB}
import org.scalacheck._
import scodec.bits.ByteVector

import scala.concurrent.duration._

object testkit {
  val testMiner =
    SimAccount(Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync(), BigInt("1000000000000000000000000"), 0)

  val testAlloc =
    Map(testMiner.address -> testMiner.balance)

  val testGenesis = GenesisConfig.generate(0, testAlloc)

  def fillConfigs(n: Int): List[Config] =
    (0 until n).toList.map { i =>
      TypeSafeConfigHelper.withIdentityAndPort(s"test-node-${i}", 20000 + (i * 3))
    }

  val genesis = Clique.generateGenesisConfig(testGenesis, List(testMiner.address))

  def fillFullNodeConfigs(n: Int): List[FullNodeConfig] = {
    fillConfigs(n).map { config =>
      val fnc = ConfigLoader.loadFullNodeConfig[IO](config).unsafeRunSync()
      fnc
        .copy(
          genesisOrPath = Left(genesis)
        )
        .withHistory(_.copy(dbBackend = "inmem"))
        .withMining(_.copy(minerAddressOrKey = Right(testMiner.keyPair), period = 100.millis))
        .withPeer(
          _.copy(dbBackend = "inmem", nodekeyOrPath = Left(Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())))
    }
  }

  val testConfig: FullNodeConfig = fillFullNodeConfigs(1).head

  def istanbulTestConfig(keypairs: List[KeyPair]): FullNodeConfig = {
    val genesisConfig = prepareIstanbulConfig(keypairs, testMiner)
    testConfig.copy(
      consensusAlgo = "istanbul",
      genesisOrPath = Left(genesisConfig)
    )
  }

  def genConsensus(implicit config: FullNodeConfig): Gen[Consensus[IO]] = {
    implicit val chainId = config.genesis.chainId
    val p = config.consensusAlgo match {
      case "clique" =>
        for {
          history   <- History.forBackendAndPath[IO](config.history.dbBackend, config.history.chainDataDir)
          blockPool <- BlockPool(history, BlockPoolConfig())
          clique    <- Clique(config.mining, config.genesis, history, Some(testMiner.keyPair))
          consensus = new CliqueConsensus[IO](clique, blockPool)
        } yield consensus
      case "istanbul" =>
        genIstanbulConsensus(testMiner.keyPair)
    }
    p.unsafeRunSync()
  }

  def genIstanbulConsensus(selfPk: KeyPair)(implicit config: FullNodeConfig): IO[Consensus[IO]] = {
    implicit val cache = CacheBuilder.build[IO, Snapshot](128).unsafeRunSync()
    for {
      history   <- History.forBackendAndPath[IO](config.history.dbBackend, config.history.chainDataDir)
      blockPool <- BlockPool[IO](history, BlockPoolConfig())
      promise   <- Deferred[IO, Int]
      istanbul  <- Istanbul[IO](IstanbulConfig(), history, config.genesis, selfPk, StateNewRound)
      consensus = new IstanbulConsensus[IO](blockPool, istanbul)
    } yield consensus
  }

  def sign(sigHash: ByteVector, pk: KeyPair)(implicit chainId: BigInt): CryptoSignature =
    Signature[ECDSA].sign[IO](sigHash.kec256.toArray, pk, chainId).unsafeRunSync()

  private def prepareIstanbulConfig(keypairs: List[KeyPair], miner: SimAccount): GenesisConfig = {
    implicit val byteArrayOrd: Ordering[Array[Byte]] = new Ordering[Array[Byte]] {
      def compare(a: Array[Byte], b: Array[Byte]): Int =
        if (a eq null) {
          if (b eq null) 0
          else -1
        } else if (b eq null) 1
        else {
          val L = math.min(a.length, b.length)
          var i = 0
          while (i < L) {
            if (a(i) < b(i)) return -1
            else if (b(i) < a(i)) return 1
            i += 1
          }
          if (L < b.length) -1
          else if (L < a.length) 1
          else 0
        }
    }

    implicit val addressOrd: Ordering[Address] = Ordering.by(_.bytes.toArray)

    val signers    = keypairs
    val validators = signers.map(Address(_))

    val alloc = Map(miner.address -> miner.balance)
    val extra = IstanbulExtra(validators, ByteVector.empty, List.empty)
    val genesisConfig = GenesisConfig
      .generate(chainId, alloc)
//      .copy(difficulty = BigInt(1),
//            extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes)
//    val genesisConfig =
//      testReference.genesis.copy(
//        alloc = alloc,
//        extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes)

    val sigHash = Istanbul.sigHash(genesisConfig.header)
    val seal    = sign(sigHash, miner.keyPair)

    val commitSeals = signers.map(pk => {
      sign(genesisConfig.header.hash ++ ByteVector(IstanbulMessage.msgCommitCode), pk)
    })
    val newExtra = IstanbulExtra(validators, ByteVector(seal.bytes), commitSeals)

    val sealConfig = genesisConfig
//      .copy(extraData = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(newExtra).require.bytes)

    sealConfig
  }

  implicit def arbConsensus(implicit config: FullNodeConfig): Arbitrary[Consensus[IO]] = Arbitrary {
    genConsensus(config)
  }

  implicit def arbHistory(implicit config: FullNodeConfig): Arbitrary[History[IO]] = Arbitrary {
    val consensus = random[Consensus[IO]](arbConsensus(config))
    consensus.history
  }

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

  implicit def arbSignedTransaction(implicit config: FullNodeConfig): Arbitrary[SignedTransaction] = Arbitrary {
    implicit val chainId = config.genesis.chainId
    for {
      tx <- arbTransaction.arbitrary
      keyPair = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
      stx     = SignedTransaction.sign[IO](tx, keyPair).unsafeRunSync()
    } yield stx
  }

  implicit def arbBlockHeader: Arbitrary[BlockHeader] = Arbitrary {
    for {
      parentHash       <- genBoundedByteVector(32, 32)
      ommersHash       <- genBoundedByteVector(32, 32)
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
//      extraData        <- arbByteVector.arbitrary
//      mixHash          <- genBoundedByteVector(32, 32)
//      nonce            <- genBoundedByteVector(8, 8)
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
        extra = ByteVector.empty
//        extraData = extraData,
//        mixHash = mixHash,
//        nonce = nonce
      )
  }

  implicit def arbBlockBody(implicit config: FullNodeConfig): Arbitrary[BlockBody] = Arbitrary {
    for {
      stxs <- arbTxs.arbitrary
    } yield BlockBody(stxs, Nil)
  }

  implicit def arbSignedTransactions(implicit config: FullNodeConfig): Arbitrary[SignedTransactions] = Arbitrary {
    for {
      txs <- arbTxs.arbitrary
    } yield SignedTransactions(txs)
  }

  implicit def arbBlock(implicit config: FullNodeConfig): Arbitrary[Block] = Arbitrary {
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

  implicit def arbPeerManager(implicit config: FullNodeConfig): Arbitrary[PeerManager[IO]] = Arbitrary {
    genPeerManager(config)
  }

  def genTxPool(implicit config: FullNodeConfig): Gen[TxPool[IO]] = {
    val pm = random[PeerManager[IO]]
    TxPool[IO](config.txPool, pm).unsafeRunSync
  }

  implicit def arbTxPool(implicit config: FullNodeConfig): Arbitrary[TxPool[IO]] = Arbitrary {
    genTxPool(config)
  }

  def genStatus(number: BigInt = 0)(implicit config: FullNodeConfig): Gen[Status] =
    Gen.delay(Status(config.genesis.chainId, config.genesis.header.hash, number))

  def genPeer(implicit config: FullNodeConfig): Gen[Peer[IO]] =
    for {
      keyPair <- arbKeyPair.arbitrary
      status  <- genStatus()
    } yield Peer.dummy[IO](keyPair.public, status).unsafeRunSync()

  implicit def arbPeer(implicit config: FullNodeConfig): Arbitrary[Peer[IO]] = Arbitrary {
    genPeer
  }

  def genPeers(min: Int, max: Int)(implicit config: FullNodeConfig): Gen[List[Peer[IO]]] =
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

  def genTxs(min: Int = 0, max: Int = 1024)(implicit config: FullNodeConfig): Gen[List[SignedTransaction]] = {
    implicit val chainId = config.genesis.chainId
    for {
      size <- Gen.chooseNum(min, max)
      generator = TxGenerator(testMiner).unsafeRunSync()
      txs       = generator.genTxs.take(size).compile.toList.unsafeRunSync()
    } yield txs
  }

  implicit def arbTxs(implicit config: FullNodeConfig): Arbitrary[List[SignedTransaction]] = Arbitrary {
    genTxs()(config)
  }

  def genBlockMiner(implicit config: FullNodeConfig): Gen[BlockMiner[IO]] = {
    val sm    = random[SyncManager[IO]]
    val miner = BlockMiner[IO](config.mining, sm).unsafeRunSync()
    miner
  }

  implicit def arbBlockMiner(implicit config: FullNodeConfig): Arbitrary[BlockMiner[IO]] = Arbitrary {
    genBlockMiner(config)
  }

  def genBlocks(min: Int, max: Int)(implicit config: FullNodeConfig): Gen[List[Block]] =
    for {
      miner <- genBlockMiner(config)
      size  <- Gen.chooseNum(min, max)
      blocks = miner.stream.take(size).compile.toList.unsafeRunSync()
    } yield blocks.map(_.block)

  def genBlock(
      parentOpt: Option[Block] = None,
      stxsOpt: Option[List[SignedTransaction]] = None,
      ommersOpt: Option[List[BlockHeader]] = None
  )(implicit config: FullNodeConfig): Gen[Block] =
    for {
      miner <- genBlockMiner(config)
      mined = miner.mine1(parentOpt, stxsOpt, ommersOpt).unsafeRunSync()
    } yield mined.block

  def genPeerManager(implicit config: FullNodeConfig): Gen[PeerManager[IO]] = {
    implicit val chainId: BigInt = config.genesis.chainId
    val consensus                = random[Consensus[IO]]
    val history                  = consensus.history
    PeerManagerPlatform[IO](config.peer, history).unsafeRunSync()
  }

  def genBlockPool(implicit config: FullNodeConfig): Gen[BlockPool[IO]] = {
    val consensus = random[Consensus[IO]]
    val history   = consensus.history
    BlockPool(history, BlockPoolConfig()).unsafeRunSync()
  }

  implicit def arbBlockPool(implicit config: FullNodeConfig): Arbitrary[BlockPool[IO]] = Arbitrary {
    genBlockPool(config)
  }

  def genOmmerPool(poolSize: Int = 30)(implicit config: FullNodeConfig): Gen[OmmerPool[IO]] = {
    val consensus = random[Consensus[IO]]
    val history   = consensus.history
    OmmerPool(history, poolSize).unsafeRunSync()
  }

  implicit def arbOmmerPool(implicit config: FullNodeConfig): Arbitrary[OmmerPool[IO]] = Arbitrary {
    genOmmerPool()(config)
  }

  implicit def arbBlockExecutor(implicit config: FullNodeConfig): Arbitrary[BlockExecutor[IO]] = Arbitrary {
    implicit val chainId = config.genesis.chainId
    val consensus        = random[Consensus[IO]]
    val pm = PeerManagerPlatform[IO](config.peer, consensus.history)
      .unsafeRunSync()
    BlockExecutor[IO](config.history, consensus, pm).unsafeRunSync()
  }

  def genFullSync(implicit config: FullNodeConfig): Gen[FullSync[IO]] = {
    val executor   = random[BlockExecutor[IO]]
    val syncStatus = Ref.of[IO, SyncStatus](SyncStatus.Booting).unsafeRunSync()
    FullSync[IO](config.sync, executor, syncStatus)
  }

  def genSyncManager(status: SyncStatus = SyncStatus.Booting)(implicit config: FullNodeConfig): Gen[SyncManager[IO]] = {
    val executor = random[BlockExecutor[IO]]
    SyncManager[IO](config.sync, executor, status).unsafeRunSync()
  }

  implicit def arbSyncManager(implicit config: FullNodeConfig): Arbitrary[SyncManager[IO]] = Arbitrary {
    genSyncManager()(config)
  }

  def genDiscovery(implicit config: FullNodeConfig): Gen[Discovery[IO]] = {
    val (transport, _) = UdpTransport[IO](config.peer.discoveryAddr).allocated.unsafeRunSync()
    val keyPair        = random[KeyPair]
    val db             = random[KeyValueDB[IO]]
    val store          = PeerStorePlatform.fromKV(db)
    Discovery[IO](config.peer, transport, keyPair, store).unsafeRunSync()
  }

  def genPeerTable: Gen[PeerTable[IO]] = {
    val peerNode = PeerNode(random[KeyPair].public, "localhost", 10000, 0, PeerType.Trusted)
    val db       = random[KeyValueDB[IO]]
    val store    = PeerStorePlatform.fromKV(db)
    PeerTable(peerNode, store, Vector.empty).unsafeRunSync()
  }

  implicit def arbPeerTable: Arbitrary[PeerTable[IO]] = Arbitrary {
    genPeerTable
  }

  implicit def arbPeerNode: Arbitrary[PeerNode] = Arbitrary {
    for {
      port <- Gen.chooseNum(10000, 60000)
      kp = random[KeyPair]
    } yield PeerNode(kp.public, "localhost", port, 0, PeerType.Trusted)
  }
}
