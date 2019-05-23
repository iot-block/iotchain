package jbok.core.consensus.poa.clique

import cats.effect.IO
import cats.effect.concurrent.Ref
import jbok.codec.rlp.implicits._
import jbok.common.testkit._
import jbok.core.CoreSpec
import jbok.core.ledger.History
import jbok.core.models.{Address, BlockHeader}
import jbok.core.testkit._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

import scala.collection.mutable

final case class TestVote(
    signer: String,
    voted: String = "",
    auth: Boolean = false
)

final case class Test(signers: List[String], votes: List[TestVote], results: List[String], epoch: Int = 30000)

trait SnapshotFixture extends CoreSpec {
  def mkHistory(signers: List[Address]) = {
    val config           = genesis.copy(miners = signers)
    implicit val chainId = config.chainId
    val db               = KeyValueDB.inmem[IO].unsafeRunSync()
    val history          = History(db)
    history.initGenesis(config).unsafeRunSync()
    history
  }

  val accounts: mutable.Map[String, KeyPair] = mutable.Map.empty

  def address(account: String): Address = {
    if (!accounts.contains(account)) {
      accounts += (account -> Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    }
    Address(accounts(account))
  }

  def sign(header: BlockHeader, signer: String)(implicit chainId: BigInt): BlockHeader = {
    if (!accounts.contains(signer)) {
      accounts += (signer -> Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync())
    }
    val sig = Signature[ECDSA]
      .sign[IO](Clique.sigHash[IO](header).unsafeRunSync().toArray, accounts(signer), chainId)
      .unsafeRunSync()
    val extra      = RlpCodec.decode[CliqueExtra](header.extra.bits).require.value.copy(signature = sig)
    val extraBytes = extra.asValidBytes
//    val signed    = header.copy(extraData = header.extraData.dropRight(65) ++ ByteVector(sig.bytes))
    val signed    = header.copy(extra = extraBytes)
    val recovered = Clique.ecrecover[IO](signed, genesis.chainId).unsafeRunSync().get
    require(recovered == Address(accounts(signer)), s"recovered: ${recovered}, signer: ${accounts(signer)}")
    signed
  }
}

class SnapshotSpec extends CoreSpec {
  def check(test: Test) = new SnapshotFixture {
    val miningConfig  = config.mining.copy(epoch = test.epoch)
    val signers       = test.signers.map(signer => address(signer))
    val genesisConfig = genesis.copy(miners = signers)
    val db            = KeyValueDB.inmem[IO].unsafeRunSync()
    val history       = History(db)

    history.initGenesis(genesisConfig).unsafeRunSync()

    // Assemble a chain of headers from the cast votes
    val headers: List[BlockHeader] = test.votes.zipWithIndex.map {
      case (v, i) =>
        val number   = BigInt(i) + 1
        val time     = i * miningConfig.period.toSeconds
        val coinbase = address(v.voted)
        val extra = CliqueExtra(
          Nil,
          CryptoSignature(ByteVector.fill(65)(0).toArray),
          Some(Proposal(coinbase, v.auth))
        )
        val extraBytes = extra.asValidBytes
        val header = random[BlockHeader]
          .copy(
            number = number,
            unixTimestamp = time,
            extra = extraBytes,
          )
        sign(header, v.signer) // signer vote to authorize/deauthorize the beneficiary
    }

    val head           = headers.last
    val keyValueDB     = KeyValueDB.inmem[IO].unsafeRunSync()
    val kp             = Signature[ECDSA].generateKeyPair[IO]().unsafeRunSync()
    val proposal = Ref.of[IO, Option[Proposal]](None).unsafeRunSync()
    val clique         = new Clique[IO](miningConfig, db, history, proposal, kp)
    val snap           = clique.applyHeaders(head.number, head.hash, headers).unsafeRunSync()
    val updatedSigners = snap.getSigners
    import Snapshot.addressOrd
    val expectedSigners = test.results.map(address).sorted

    updatedSigners shouldBe expectedSigners
  }

  "Snapshot" should {
    "sigHash and ecrecover" in new SnapshotFixture {
      val signer   = address("A")
      val coinbase = address("B")
      val extra = CliqueExtra(
        Nil,
        CryptoSignature(ByteVector.fill(65)(0).toArray),
        Some(Proposal(coinbase, true))
      )
      val extraBytes = extra.asValidBytes
      val header     = random[BlockHeader].copy(extra = extraBytes)
      val signed     = sign(header, "A")(genesis.chainId)
      Clique.ecrecover[IO](signed, genesis.chainId).unsafeRunSync().get shouldBe signer
    }

    "single signer, no votes cast" in {
      val test = Test(List("A"), List(TestVote("A")), List("A"))
      check(test)
    }

    "single signer, voting to add two others (only accept first, second needs 2 votes)" in {
      val test = Test(
        List("A"),
        List(TestVote("A", "B", auth = true), TestVote("B"), TestVote("A", "C", auth = true)),
        List("A", "B")
      )
      check(test)
    }

    "two singers, continuous signing" in {
      val test = Test(
        List("A", "B"),
        List(
          TestVote("A", "C", auth = true),
          TestVote("B", "D", auth = true),
          TestVote("A", "C", auth = true),
          TestVote("B", "D", auth = true)
        ),
        List("A", "B")
      )
      check(test)
    }

    "two signers, voting to add three others (only accept first two, third needs 3 votes already)" in {
      val test = Test(
        List("A", "B"),
        List(
          TestVote("A", "C", true),
          TestVote("B", "C", true),
          TestVote("A", "D", true),
          TestVote("B", "D", true),
          TestVote("C"),
          TestVote("A", "E", true),
          TestVote("B", "E", true)
        ),
        List("A", "B", "C", "D")
      )
      check(test)
    }

    "single signer, dropping itself (weird, but one less corner case by explicitly allowing this)" in {
      val test = Test(
        List("A"),
        List(TestVote("A", "A", false)),
        Nil
      )
      check(test)
    }

    "two signers, actually needing mutual consent to drop either of them (not fulfilled)" in {
      val test = Test(
        List("A", "B"),
        List(TestVote("A", "B", false)),
        List("A", "B")
      )
      check(test)
    }

    "two signers, actually needing mutual consent to drop either of them (fulfilled)" in {
      val test = Test(
        List("A", "B"),
        List(TestVote("A", "B", false), TestVote("B", "B", false)),
        List("A")
      )
      check(test)
    }

    "three signers, two of them deciding to drop the third" in {
      val test = Test(
        "A" :: "B" :: "C" :: Nil,
        TestVote("A", "C", false) :: TestVote("B", "C", false) :: Nil,
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "four signers, consensus of two not being enough to drop anyone" in {
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: Nil,
        TestVote("A", "C", false) :: TestVote("B", "C", false) :: Nil,
        "A" :: "B" :: "C" :: "D" :: Nil
      )
      check(test)
    }

    "four signers, consensus of three already being enough to drop someone" in {
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: Nil,
        TestVote("A", "D", false) :: TestVote("B", "D", false) :: TestVote("C", "D", false) :: Nil,
        "A" :: "B" :: "C" :: Nil
      )
      check(test)
    }

    "authorizations are counted once per signer per target" in {
      val test = Test(
        "A" :: "B" :: Nil,
        List(
          TestVote("A", "C", true),
          TestVote("B"),
          TestVote("A", "C", true),
          TestVote("B"),
          TestVote("A", "C", true)
        ),
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "authorizing multiple accounts concurrently is permitted" in {
      val test = Test(
        "A" :: "B" :: Nil,
        List(
          TestVote("A", "C", true),
          TestVote("B"),
          TestVote("A", "D", true),
          TestVote("B"),
          TestVote("A"),
          TestVote("B", "C", true),
          TestVote("A"),
          TestVote("B", "D", true)
        ),
        "A" :: "B" :: "C" :: "D" :: Nil
      )
      check(test)
    }

    "deauthorizations are counted once per signer per target" in {
      val test = Test(
        "A" :: "B" :: Nil,
        List(
          TestVote("A", "B", false),
          TestVote("B"),
          TestVote("A", "B", false),
          TestVote("B"),
          TestVote("A", "B", false)
        ),
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "deauthorizing multiple accounts concurrently is permitted" in {
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: Nil,
        List(
          TestVote("A", "C", false),
          TestVote("B"),
          TestVote("C"),
          TestVote("A", "D", false),
          TestVote("B"),
          TestVote("C"),
          TestVote("A"),
          TestVote("B", "D", false),
          TestVote("C", "D", false),
          TestVote("A"),
          TestVote("B", "C", false)
        ),
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "Votes from deauthorized signers are discarded immediately (deauth votes)" in {
      val test = Test(
        "A" :: "B" :: "C" :: Nil,
        List(
          TestVote("C", "B", false),
          TestVote("A", "C", false),
          TestVote("B", "C", false),
          TestVote("A", "B", false)
        ),
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "Votes from deauthorized signers are discarded immediately (auth votes)" in {
      val test = Test(
        "A" :: "B" :: "C" :: Nil,
        List(
          TestVote("C", "B", false),
          TestVote("A", "C", false),
          TestVote("B", "C", false),
          TestVote("A", "B", false)
        ),
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "cascading changes are not allowed, only the the account being voted on may change" in {
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: Nil,
        List(
          TestVote("A", "C", false),
          TestVote("B"),
          TestVote("C"),
          TestVote("A", "D", false),
          TestVote("B", "C", false),
          TestVote("C"),
          TestVote("A"),
          TestVote("B", "D", false),
          TestVote("C", "D", false)
        ),
        "A" :: "B" :: "C" :: Nil
      )
      check(test)
    }

    "changes reaching consensus out of bounds (via a deauth) execute on touch" in {
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: Nil,
        List(
          TestVote("A", "C", false),
          TestVote("B"),
          TestVote("C"),
          TestVote("A", "D", false),
          TestVote("B", "C", false),
          TestVote("C"),
          TestVote("A"),
          TestVote("B", "D", false),
          TestVote("C", "D", false),
          TestVote("A"),
          TestVote("C", "C", true)
        ),
        "A" :: "B" :: Nil
      )
      check(test)
    }

    "changes reaching consensus out of bounds (via a deauth) may go out of consensus on first touch" in {
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: Nil,
        List(
          TestVote("A", "C", false),
          TestVote("B"),
          TestVote("C"),
          TestVote("A", "D", false),
          TestVote("B", "C", false),
          TestVote("C"),
          TestVote("A"),
          TestVote("B", "D", false),
          TestVote("C", "D", false),
          TestVote("A"),
          TestVote("B", "C", true)
        ),
        "A" :: "B" :: "C" :: Nil
      )
      check(test)
    }

    "ensure that pending votes don't survive authorization status changes" in {
      // this corner case can only appear if a signer is quickly added, remove and then
      // readded (or the inverse), while one of the original voters dropped. If a
      // past vote is left cached in the system somewhere, this will interfere with
      // the final signer outcome.
      val test = Test(
        "A" :: "B" :: "C" :: "D" :: "E" :: Nil,
        List(
          TestVote("A", "F", true), // authorize F, 3 votes needed
          TestVote("B", "F", true),
          TestVote("C", "F", true),
          TestVote("D", "F", false), // Deauthorize F, 4 votes needed (leave A's previous vote "unchanged")
          TestVote("E", "F", false),
          TestVote("B", "F", false),
          TestVote("C", "F", false),
          TestVote("D", "F", true), // Almost authorize F, 2/3 votes needed
          TestVote("E", "F", true),
          TestVote("B", "A", false), // Deauthorize A, 3 votes needed
          TestVote("C", "A", false), // Deauthorize A, 3 votes needed
          TestVote("D", "A", false), // Deauthorize A, 3 votes needed
          TestVote("B", "F", true) // Finish authorizing F, 3/3 votes needed
        ),
        "B" :: "C" :: "D" :: "E" :: "F" :: Nil
      )
      check(test)
    }

    "epoch transitions reset all votes to allow chain checkpointing" in {
      val test = Test(
        "A" :: "B" :: Nil,
        List(
          TestVote("A", "C", true),
          TestVote("B"),
          TestVote("A"), // Checkpoint block, (don't vote here, it's validated outside of snapshots)
          TestVote("B", "C", true)
        ),
        "A" :: "B" :: Nil,
        3
      )
      check(test)
    }
  }
}
