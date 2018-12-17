package jbok.core.consensus.istanbul

import cats.effect.IO
import cats.effect.concurrent.{Deferred, Ref}
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.core.models.Address
import jbok.crypto.signature.{ECDSA, KeyPair, Signature}
import jbok.persistent.{CacheBuilder, KeyValueDB}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import cats.implicits._
import cats.effect.implicits._
import jbok.core.ledger.History
import jbok.core.config.reference
import jbok.crypto._
import fs2._

import scala.concurrent.ExecutionContext.global
import scala.collection.mutable.{ArrayBuffer, Map => MMap}
import scala.collection.mutable

class StateSpec extends JbokSpec {
  implicit val cs    = IO.contextShift(global)
  implicit val timer = IO.timer(global)

  private def fillExtraData(signers: List[Address], pk: KeyPair): ByteVector = {
    val extra = IstanbulExtra(signers, ByteVector.empty, List.empty)

    val seal = ByteVector(
      Signature[ECDSA].sign[IO](RlpCodec.encode(extra).require.bytes.kec256.toArray, pk, 0).unsafeRunSync().bytes)

    ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra.copy(seal = seal)).require.bytes
  }

  def mkHistory(signers: List[Address], pk: KeyPair) = {
    val extra   = fillExtraData(signers, pk)
    val config  = reference.genesis.copy(extraData = extra)
    val db      = KeyValueDB.inmem[IO].unsafeRunSync()
    val history = History[IO](db).unsafeRunSync()
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

  def mkClient(validators: List[String], proposer: String, self: String): Istanbul[IO] = {
    val signers    = validators.map(address(_))
    val backlogs   = Ref.unsafe[IO, MMap[Address, ArrayBuffer[Message]]](MMap.empty)
    val proposerPk = accounts.get(proposer).get
    val selfPk     = accounts.get(self).get
    val current =
      Ref.unsafe[IO, RoundState](RoundState(0, 1, None, MessageSet.empty, MessageSet.empty, ByteVector.empty, false))
    val roundChanges   = Ref.unsafe[IO, MMap[BigInt, MessageSet]](MMap.empty)
    val validatorSet   = Ref.unsafe[IO, ValidatorSet](ValidatorSet(address(proposer), ArrayBuffer.empty ++ signers))
    val promise        = Ref.unsafe[IO, Deferred[IO, BigInt]](Deferred[IO, BigInt].unsafeRunSync())
    val state          = Ref.unsafe[IO, State](StateNewRound)
    implicit val cache = CacheBuilder.build[IO, Snapshot](128).unsafeRunSync()
    Istanbul[IO](IstanbulConfig(),
                 mkHistory(signers, selfPk),
                 MMap.empty,
                 backlogs,
                 selfPk,
                 current,
                 roundChanges,
                 validatorSet,
                 promise,
                 state)
  }

  "istanbul" should {
    "extra data" in {
      val signers = List("A", "B", "C").map(address(_))

      val extra     = IstanbulExtra(signers, ByteVector.empty, List.empty)
      val rlpData   = RlpCodec.encode(extra).require.bytes
      val extraFill = ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ rlpData

      val dropedData = extraFill.drop(Istanbul.extraVanity).bits
      val extraData  = RlpCodec.decode[IstanbulExtra](dropedData).require.value
      println(extraData)
    }
  }

  "state" should {
    "timeout" in {
      val validators = List("A", "B", "C")
      val clientA    = mkClient(validators, "A", "A")
      val clientB    = mkClient(validators, "A", "B")
      val clientC    = mkClient(validators, "A", "C")
      for {
        _ <- Stream(clientA, clientB, clientC)
      }
//      clientA.startNewRound(0).unsafeRunSync()
      println("complete")
    }
  }
}
