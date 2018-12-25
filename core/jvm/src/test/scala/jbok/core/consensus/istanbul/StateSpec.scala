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
import jbok.core.config.Configs
import jbok.core.consensus.istanbul.Snapshot._
import jbok.core.messages.IstanbulMessage

import scala.collection.mutable.{Map => MMap}

case class Expect(
    state: State,
    proposer: Address,
    validators: List[Address],
    prepares: List[Address],
    commits: List[Address],
    isLocked: Boolean,
    waitingForRoundChange: Boolean,
    round: Int,
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
      validators = validatorSet.validators.sorted,
      prepares = roundState.prepares.messages.toList.map(_._1).sorted,
      commits = roundState.commits.messages.toList.map(_._1).sorted,
      isLocked = roundState.isLocked,
      waitingForRoundChange = roundState.waitingForRoundChange,
      round = roundState.round,
      sequence = roundState.blockNumber,
      preprepare = roundState.preprepare
    )
  }
}

class StateSpec extends JbokSpec {
  val accounts: MMap[String, KeyPair] = MMap("A" -> testMiner.keyPair)

  val validatorNames: List[String]            = List("A", "B", "C", "D")
  val validators: List[KeyPair]               = validatorNames.map(account)
  val minerName: String                       = "A"
  val miner: KeyPair                          = account(minerName)
  implicit val config: Configs.FullNodeConfig = istanbulTestConfig(validators)

  def F: Int = Math.ceil(validators.size / 3.0).toInt - 1

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

  def node(name: String): Istanbul[IO] = {
    val consensus: IstanbulConsensus[IO] =
      genIstanbulConsensus(account(name)).unsafeRunSync().asInstanceOf[IstanbulConsensus[IO]]
    consensus.istanbul
  }
  def nodeMiner: Istanbul[IO] = node(minerName)

  def randomBlock: Block =
    random[List[Block]](genBlocks(1, 1)).head

  def newRoundToPreprepared(messageNum: Int): Unit = {
    val block      = randomBlock
    val preprepare = Preprepare(View(0, 1), block)

    val istanbul = nodeMiner
    istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

    val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get

    val prepares = validatorNames
      .take(messageNum)
      .map(name => {
        fakeReceivePrepare(subject, name, istanbul).unsafeRunSync()
        address(name)
      })

    check(
      istanbul,
      Expect(
        state = StatePreprepared,
        proposer = address("A"),
        validators = validators.map(Address(_)).sorted,
        prepares = prepares.sorted,
        commits = List.empty,
        isLocked = false,
        waitingForRoundChange = false,
        round = 0,
        sequence = 1,
        preprepare = Some(preprepare)
      )
    )
  }

  def checkPreprepareToPrepared(messageNum: Int): Unit = {
    val block      = randomBlock
    val preprepare = Preprepare(View(0, 1), block)

    val istanbul = nodeMiner
    istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

    val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
    val prepares = validatorNames
      .take(messageNum)
      .map(name => {
        fakeReceivePrepare(subject, name, istanbul).unsafeRunSync()
        address(name)
      })

    check(
      istanbul,
      Expect(
        state = StatePrepared,
        proposer = address("A"),
        validators = validators.map(Address(_)).sorted,
        prepares = prepares.sorted,
        commits = List(address("A")),
        isLocked = true,
        waitingForRoundChange = false,
        round = 0,
        sequence = 1,
        preprepare = Some(preprepare)
      )
    )
  }

  def istanbulWithReceivePrepares(preprepare: Preprepare, messageNum: Int): Istanbul[IO] = {
    val istanbul = nodeMiner
    istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()
    val subject = istanbul.currentContext.currentSubject().unsafeRunSync().get

    validatorNames
      .take(messageNum)
      .foreach(name => {
        fakeReceivePrepare(subject, name, istanbul).unsafeRunSync()
      })
    istanbul
  }

  def istanbulWithReceiveCommits(preprepare: Preprepare, messageNum: Int): Istanbul[IO] = {
    val istanbul = istanbulWithReceivePrepares(preprepare, 2 * F + 1)
    val subject  = istanbul.currentContext.currentSubject().unsafeRunSync().get
    validatorNames
      .take(messageNum)
      .foreach(name => {
        fakeReceiveCommit(subject, name, istanbul).unsafeRunSync()
      })

    istanbul
  }

  def istanbulWithReceiveRoundChanges(preprepare: Preprepare, fakeRound: Int, messageNum: Int): Istanbul[IO] = {
    val istanbul = nodeMiner
    istanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

    val subject: Subject = istanbul.currentContext.currentSubject().unsafeRunSync().get
    val fakeSubject      = subject.copy(view = subject.view.copy(round = fakeRound))

    validatorNames
      .filterNot(_ == minerName)
      .take(messageNum)
      .foreach(name => fakeReceiveRoundChange(fakeSubject, name, istanbul).unsafeRunSync())

    istanbul
  }

  def preparedWhenReceiveCommit(messageNum: Int): Unit = {
    val block      = randomBlock
    val preprepare = Preprepare(View(0, 1), block)

    val istanbul = istanbulWithReceiveCommits(preprepare, messageNum)

    check(
      istanbul,
      Expect(
        state = StatePrepared,
        proposer = address("A"),
        validators = validatorNames.map(address).sorted,
        prepares = validatorNames.map(address).take(2 * F + 1).sorted,
        commits = validatorNames.map(address).take(messageNum).sorted,
        isLocked = true,
        waitingForRoundChange = false,
        round = 0,
        sequence = 1,
        preprepare = Some(preprepare)
      )
    )
  }

  "state" should {
    "receive 1 prepare message, NewRound -> Pre-prepared" in {
      newRoundToPreprepared(1)
    }
    "receive F+1 prepare message, NewRound -> Pre-prepared" in {
      newRoundToPreprepared(F + 1)
    }
    "receive 2F prepare message, NewRound -> Pre-prepared" in {
      newRoundToPreprepared(2 * F)
    }
    "receive 2F+1 prepare messages, Pre-prepared -> Prepared" in {
      checkPreprepareToPrepared(2 * F + 1)
    }
    "receive 3F+1 prepare message, Pre-prepared -> Prepared" in {
      checkPreprepareToPrepared(3 * F + 1)
    }

    "[validator]startNewRound, waiting for proposer broadcast PRE-PREPARE message" in {
      val istanbul = node("B")
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

    "receive future message, store in backlogs" in {
      val block      = randomBlock
      val preprepare = Preprepare(View(0, 1), block)

      val istanbul = nodeMiner
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

    "receive future message, store in backlogs, and processed after state changed" in {
      val bAccount  = account("B")
      val bIstanbul = node("B")
      bIstanbul.startNewRound(0, None, false).unsafeRunSync()

      val block      = randomBlock
      val preprepare = Preprepare(View(0, 1), block)
      val aIstanbul  = node("A")
      aIstanbul.startNewRound(0, Some(preprepare), false).unsafeRunSync()

      /**
        * B is in StateNewRound, waiting for PRE-PREPARE message,
        * store PREPARE message in backlog for process later
        */
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

    "receive 1 commit message, Prepared" in {
      preparedWhenReceiveCommit(1)
    }
    "receive F+1 commit messages, Prepared" in {
      preparedWhenReceiveCommit(F + 1)
    }
    "receive 2F commit messages, Prepared" in {
      preparedWhenReceiveCommit(2 * F)
    }

    "receive 2F+1 commit messages" should {
      "block insertion success, Prepared -> Committed -> Final Committed -> NewRound" in {
        val block      = randomBlock
        val preprepare = Preprepare(View(0, 1), block)

        val istanbul = istanbulWithReceiveCommits(preprepare, 2 * F + 1)

        check(
          istanbul,
          Expect(
            state = StateNewRound,
            proposer = address("A"),
            validators = validators.map(Address(_)).sorted,
            prepares = validators.map(Address(_)).take(2 * F + 1).sorted,
            commits = validators.map(Address(_)).take(2 * F + 1).sorted,
            isLocked = false,
            waitingForRoundChange = false,
            round = 0,
            sequence = 1,
            preprepare = Some(preprepare)
          )
        )
      }

      "block insertion fail, Prepared -> Committed -> Round Change" in {
        val block      = randomBlock
        val preprepare = Preprepare(View(0, 1), block)

        val istanbul = istanbulWithReceiveCommits(preprepare, 2 * F + 1)

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

    "receive roundChange message during transition flow" should {
      def checkRoundChanges(messageNum: Int, expectState: State): Istanbul[IO] = {
        val block      = randomBlock
        val preprepare = Preprepare(View(0, 1), block)
        val fakeRound  = 2
        val istanbul   = istanbulWithReceiveRoundChanges(preprepare, fakeRound, messageNum)
        istanbul.state.get.unsafeRunSync() shouldBe expectState
        istanbul.roundChanges.get.unsafeRunSync()(fakeRound).messages.size shouldBe messageNum
        istanbul
      }
      "1 RoundChange message" in {
        checkRoundChanges(1, StatePreprepared)
      }
      "F+1 RoundChange message" in {
        checkRoundChanges(F + 1, StatePreprepared)
      }
      "2F RoundChange message" in {
        checkRoundChanges(2 * F, StatePreprepared)
      }
      "2F+1 RoundChange message" in {
        val istanbul = checkRoundChanges(2 * F + 1, StateNewRound)
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
            preprepare = Some(Preprepare(View(0, 1), randomBlock))
          )
        )
      }
    }
  }
}
