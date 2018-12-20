package jbok.core.consensus.istanbul

import cats.effect.{Concurrent, IO}
import jbok.codec.rlp.implicits._
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.core.models.{Address, Block}
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.crypto._
import jbok.common.testkit.random
import jbok.core.testkit._
import jbok.common.execution._
import jbok.core.Fixture
import jbok.core.consensus.istanbul.Snapshot._
import jbok.core.messages.IstanbulMessage
import scodec.bits.ByteVector

import scala.collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}

case class Expect(
    state: State,
    proposer: Address,
    validators: List[Address],
    prepares: List[Address],
    commits: List[Address],
    isLocked: Boolean,
    waitingForRoundChange: Boolean,
    round: BigInt,
    sequence: BigInt,
    preprepare: Option[Preprepare]
)
object Expect {
  def apply(
      istanbul: Istanbul[IO]
  ): Expect = {
    val validatorSet           = istanbul.validatorSet.get.unsafeRunSync()
    val roundState: RoundState = istanbul.currentContext.current.get.unsafeRunSync()

    Expect(
      state = istanbul.currentContext.state.get.unsafeRunSync(),
      proposer = validatorSet.proposer,
      validators = validatorSet.validators.toList.sorted,
      prepares = roundState.prepares.messages.toList.map(_._1).sorted,
      commits = roundState.commits.messages.toList.map(_._1).sorted,
      isLocked = roundState.isLocked,
      waitingForRoundChange = roundState.waitingForRoundChange,
      round = roundState.round,
      sequence = roundState.sequence,
      preprepare = roundState.preprepare
    )
  }
}

class StateSpec extends JbokSpec {
  val accounts: MMap[String, KeyPair] = MMap.empty

  val validators: List[KeyPair] = List(account("A"), account("B"), account("C"), account("D"))
  val miner: KeyPair            = account("A")

  implicit val fixture: Fixture =
    istanbulFixture(validators, miner, miner, 2001)

  def account(name: String): KeyPair = {
    if (!accounts.contains(name)) {
      accounts += (name -> Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    }
    accounts(name)
  }

  def fakeReceiveMsg[A](code: Int, payload: A, receiver: Istanbul[IO], senderContext: StateContext[IO]): IO[Unit] =
    for {
      msgPayload <- IO.delay {
        if (code == IstanbulMessage.msgPreprepareCode)
          RlpCodec
            .encode(payload.asInstanceOf[Preprepare])
            .require
            .bytes
        else
          RlpCodec
            .encode(payload.asInstanceOf[Subject])
            .require
            .bytes
      }
      message <- Proxy.finalizeMessage(code, msgPayload, senderContext)
      _       <- receiver.handleMessage(message)
    } yield ()

  def fakeReceivePreprepare(preprepare: Preprepare, sender: String, receiver: Istanbul[IO]): IO[Unit] = {
    val receiverContext = receiver.currentContext
    val senderContext   = receiverContext.copy(keyPair = account(sender))
    fakeReceiveMsg(IstanbulMessage.msgPreprepareCode, preprepare, receiver, senderContext)
  }

  def fakeReceivePrepare(subject: Subject, sender: String, receiver: Istanbul[IO]): IO[Unit] = {
    val receiverContext = receiver.currentContext
    val senderContext   = receiverContext.copy(keyPair = account(sender))
    fakeReceiveMsg(IstanbulMessage.msgPrepareCode, subject, receiver, senderContext)
  }

  def fakeReceiveCommit(subject: Subject, sender: String, receiver: Istanbul[IO]): IO[Unit] = {
    val receiverContext = receiver.currentContext
    val senderContext   = receiverContext.copy(keyPair = account(sender))
    fakeReceiveMsg(IstanbulMessage.msgCommitCode, subject, receiver, senderContext)
  }

  def fakeReceiveRoundChange(subject: Subject, sender: String, receiver: Istanbul[IO]): IO[Unit] = {
    val receiverContext = receiver.currentContext
    val senderContext   = receiverContext.copy(keyPair = account(sender))
    fakeReceiveMsg(IstanbulMessage.msgRoundChange, subject, receiver, senderContext)
  }

  def check(istanbul: Istanbul[IO], expect: Expect): Unit = {
    val acutal = Expect(istanbul)
    acutal.state shouldBe expect.state
    acutal.proposer shouldBe expect.proposer
    acutal.validators shouldBe expect.validators
    acutal.prepares shouldBe expect.prepares
    acutal.commits shouldBe expect.commits
    acutal.isLocked shouldBe expect.isLocked
    acutal.waitingForRoundChange shouldBe expect.waitingForRoundChange
    acutal.round shouldBe expect.round
    acutal.sequence shouldBe expect.sequence
    acutal.preprepare shouldBe expect.preprepare
  }

  def address(name: String): Address = Address(account(name))

  "state" should {
    "[proposer]NewRound -> Pre-prepared" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StatePreprepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A")),
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
    }

    "[validator]still NewRound" in {
      val bAccount = account("B")
      val bFixture = istanbulFixture(validators, miner, bAccount, 2002)
      val consensus: IstanbulConsensus[IO] = bFixture.consensus
        .unsafeRunSync()
        .asInstanceOf[IstanbulConsensus[IO]]

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, None, false).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StateNewRound,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List.empty,
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = None
        )
      )
    }

    "future message, store in backlogs" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      val subject: Subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
      val fakeSubject      = subject.copy(view = subject.view.copy(round = subject.view.round + 1))
      fakeReceivePrepare(fakeSubject, "B", istanbul).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StatePreprepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A")).sorted,
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )

      istanbul.backlogs.get.unsafeRunSync().map(log => (log._1, log._2.size)) shouldBe Map((address("B"), 1))
    }

    "future message, store in backlogs, and messages in backlogs are processed after state transition" in {
      val bAccount = account("B")
      val bFixture = istanbulFixture(validators, miner, bAccount, 2002)
      val consensus: IstanbulConsensus[IO] = bFixture.consensus
        .unsafeRunSync()
        .asInstanceOf[IstanbulConsensus[IO]]

      val bIstanbul = consensus.istanbul
      bIstanbul.startNewRound(0, None, false).unsafeRunSync()

      val aConsensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                             = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                        = Preprepare(View(0, 1), block)
      val aIstanbul                         = aConsensus.istanbul
      aIstanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      // store in backlog
      val subject: Subject = aIstanbul.currentContext.currentSubject().unsafeRunSync().get
      fakeReceiveMsg(IstanbulMessage.msgPrepareCode, subject, bIstanbul, aIstanbul.currentContext).unsafeRunSync()
      check(
        bIstanbul,
        Expect(
          state = StateNewRound,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List.empty,
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = None
        )
      )
      bIstanbul.keyPair shouldBe bAccount
      bIstanbul.backlogs.get.unsafeRunSync().map(log => (log._1, log._2.size)) shouldBe Map((address("A"), 1))

      // process PREPARE message in backlog after receive PREPREPARE message
      fakeReceiveMsg(IstanbulMessage.msgPreprepareCode, preprepare, bIstanbul, aIstanbul.currentContext)
        .unsafeRunSync()
      check(
        bIstanbul,
        Expect(
          state = StatePreprepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A"), address("B")).sorted,
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
      bIstanbul.backlogs.get.unsafeRunSync().map(log => (log._1, log._2.size)) shouldBe Map((address("A"), 0))
    }

    "receive 2 prepare message, still Pre-prepared" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
      fakeReceivePrepare(subject, "B", istanbul).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StatePreprepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A"), address("B")).sorted,
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
    }

    "receive 3 prepare message, Pre-prepared -> Prepared" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
      fakeReceivePrepare(subject, "B", istanbul).unsafeRunSync()
      fakeReceivePrepare(subject, "C", istanbul).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StatePrepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A"), address("B"), address("C")).sorted,
          commits = List(address("A")),
          isLocked = true,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
    }

    "receive 4 prepare message, Pre-prepared -> Prepared" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
      fakeReceivePrepare(subject, "B", istanbul).unsafeRunSync()
      fakeReceivePrepare(subject, "C", istanbul).unsafeRunSync()
      fakeReceivePrepare(subject, "D", istanbul).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StatePrepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A"), address("B"), address("C"), address("D")).sorted,
          commits = List(address("A")),
          isLocked = true,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
    }

    "receive 2 commit message, still Prepared" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
      fakeReceivePrepare(subject, "B", istanbul).unsafeRunSync()
      fakeReceivePrepare(subject, "C", istanbul).unsafeRunSync()

      fakeReceiveCommit(subject, "B", istanbul).unsafeRunSync()

      check(
        istanbul,
        Expect(
          state = StatePrepared,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A"), address("B"), address("C")).sorted,
          commits = List(address("A"), address("B")).sorted,
          isLocked = true,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
    }

    "receive 3 commit messages" should {
      "block insertion success, Prepared -> Committed -> Final Committed -> NewRound" in {
        val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
        val block                            = random[List[Block]](genBlocks(1, 1)).head
        val preprepare                       = Preprepare(View(0, 1), block)

        val istanbul = consensus.istanbul
        istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

        val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
        fakeReceivePrepare(subject, "B", istanbul).unsafeRunSync()
        fakeReceivePrepare(subject, "C", istanbul).unsafeRunSync()

        fakeReceiveCommit(subject, "B", istanbul).unsafeRunSync()
        fakeReceiveCommit(subject, "C", istanbul).unsafeRunSync()

        check(
          istanbul,
          Expect(
            state = StateNewRound,
            proposer = address("A"),
            validators = validators.map(Address(_)).sorted,
            prepares = List(address("A"), address("B"), address("C")).sorted,
            commits = List(address("A"), address("B"), address("C")).sorted,
            isLocked = false,
            waitingForRoundChange = false,
            round = 0,
            sequence = 1,
            preprepare = Some(preprepare)
          )
        )
      }

      "block insertion fail, Prepared -> Committed -> Round Change" in {
        val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
        val block                            = random[List[Block]](genBlocks(1, 1)).head
        val preprepare                       = Preprepare(View(0, 1), block)

        val istanbul = consensus.istanbul
        istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

        val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
        fakeReceivePrepare(subject, "B", istanbul).unsafeRunSync()
        fakeReceivePrepare(subject, "C", istanbul).unsafeRunSync()

        fakeReceiveCommit(subject, "B", istanbul).unsafeRunSync()
        fakeReceiveCommit(subject, "C", istanbul).unsafeRunSync()

        check(
          istanbul,
          Expect(
            state = StateRoundChange,
            proposer = address("A"),
            validators = validators.map(Address(_)).sorted,
            prepares = List.empty,
            commits = List.empty,
            isLocked = false,
            waitingForRoundChange = true,
            round = 1,
            sequence = 1,
            preprepare = None
          )
        )
      }
    }

    "receive roundChange message during transition flow" in {
      val consensus: IstanbulConsensus[IO] = fixture.consensus.unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
      val block                            = random[List[Block]](genBlocks(1, 1)).head
      val preprepare                       = Preprepare(View(0, 1), block)

      val istanbul = consensus.istanbul
      istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      val subject: Subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
      val fakeRound        = 2
      val fakeSubject      = subject.copy(view = subject.view.copy(round = fakeRound))

      // receive 2 roundChange messages, still Preprepared
      fakeReceiveRoundChange(fakeSubject, "B", istanbul).unsafeRunSync()
      fakeReceiveRoundChange(fakeSubject, "C", istanbul).unsafeRunSync()
      istanbul.state.get.unsafeRunSync() shouldBe StatePreprepared
      istanbul.roundChanges.get.unsafeRunSync()(fakeRound).messages.size shouldBe 2

      // receive 3 roundCHange messages, Preprepared -> Round Change -> New Round
      fakeReceiveRoundChange(fakeSubject, "D", istanbul).unsafeRunSync()
      istanbul.roundChanges.get.unsafeRunSync()(fakeRound).messages.size shouldBe 3
      check(
        istanbul,
        Expect(
          state = StateNewRound,
          proposer = address("A"),
          validators = validators.map(Address(_)).sorted,
          prepares = List(address("A")),
          commits = List.empty,
          isLocked = false,
          waitingForRoundChange = false,
          round = 0,
          sequence = 1,
          preprepare = Some(preprepare)
        )
      )
    }

  }
}
