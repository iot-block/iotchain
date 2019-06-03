package jbok.core

import cats.effect.{Concurrent, IO}
import jbok.core.config.FullConfig
import jbok.core.messages._
import jbok.core.models._
import jbok.core.peer.{Peer, PeerUri}
import jbok.evm._
import org.scalacheck.{Arbitrary, Gen}

trait StatelessArb {
  // models
  implicit val arbUint256: Arbitrary[UInt256] = Arbitrary(StatelessGen.uint256())

  implicit val arbAccount: Arbitrary[Account] = Arbitrary(StatelessGen.account)

  implicit val arbAddress: Arbitrary[Address] = Arbitrary(StatelessGen.address)

  implicit val arbTransaction: Arbitrary[Transaction] = Arbitrary(StatelessGen.transaction)

  implicit val arbSignedTransaction: Arbitrary[SignedTransaction] = Arbitrary(StatelessGen.signedTransaction)

  implicit val arbBlockHeader: Arbitrary[BlockHeader] = Arbitrary(StatelessGen.blockHeader)

  implicit val arbBlockBody: Arbitrary[BlockBody] = Arbitrary(StatelessGen.blockBody)

  implicit val arbBlock: Arbitrary[Block] = Arbitrary(StatelessGen.block)

  implicit val arbReceipt: Arbitrary[Receipt] = Arbitrary(StatelessGen.receipt)

  // messages
  implicit val arbBlockHash: Arbitrary[BlockHash] = Arbitrary(StatelessGen.blockHash)

  implicit val arbNewBlockHashes: Arbitrary[NewBlockHashes] = Arbitrary(StatelessGen.newBlockHashes)

  implicit val arbNewBlock: Arbitrary[NewBlock] = Arbitrary(StatelessGen.newBlock)

  implicit val arbSignedTransactions: Arbitrary[SignedTransactions] = Arbitrary(StatelessGen.signedTransactions)

  implicit val arbPeerUri: Arbitrary[PeerUri] = Arbitrary(StatelessGen.peerUri)

  implicit def arbStatus(implicit config: FullConfig): Arbitrary[Status] = Arbitrary(StatelessGen.status(config))

  implicit def arbPeer(implicit F: Concurrent[IO], config: FullConfig): Arbitrary[Peer[IO]] = Arbitrary(StatelessGen.peer(config))

  // evm
  implicit val arbProgram: Arbitrary[Program] = Arbitrary(StatelessGen.program)

  implicit val argExecEnv: Arbitrary[ExecEnv] = Arbitrary(StatelessGen.execEnv)

  implicit val arbMemory: Arbitrary[Memory] = Arbitrary(StatelessGen.memory)

  implicit def arbStack(nElems: Int, elemGen: Gen[UInt256]): Arbitrary[Stack] = Arbitrary(StatelessGen.stack(nElems, elemGen))

  implicit def arbProgramContext(implicit evmConfig: EvmConfig): Arbitrary[ProgramContext[IO]] = Arbitrary(StatelessGen.programContext(evmConfig))

  implicit def arbProgramState(implicit evmConfig: EvmConfig): Arbitrary[ProgramState[IO]] = Arbitrary(StatelessGen.programState(evmConfig))
}

object StatelessArb extends StatelessArb
