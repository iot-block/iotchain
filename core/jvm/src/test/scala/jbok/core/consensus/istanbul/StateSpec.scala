package jbok.core.consensus.istanbul

import cats.effect.IO
import cats.effect.concurrent.{Deferred, Ref}
import jbok.JbokSpec
import jbok.codec.rlp.RlpCodec
import jbok.core.History
import jbok.core.config.GenesisConfig
import jbok.core.models.Address
import jbok.crypto.signature.KeyPair
import jbok.crypto.signature.ecdsa.SecP256k1
import jbok.persistent.{KeyValueDB, LruMap}
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._
import cats.implicits._
import cats.effect.implicits._
import scala.concurrent.ExecutionContext.global

import scala.collection.mutable.{ArrayBuffer, Map => MMap}
import scala.collection.mutable

class StateSpec extends JbokSpec {
  private def fillExtraData(signers: List[Address]): ByteVector = {
    val extra = IstanbulExtra(signers, ByteVector.empty, List.empty)
    ByteVector.fill(Istanbul.extraVanity)(0.toByte) ++ RlpCodec.encode(extra).require.bytes
  }

  def mkHistory(signers: List[Address]) = {
    val extra   = fillExtraData(signers)
    val config  = GenesisConfig.default.copy(extraData = extra)
    val db      = KeyValueDB.inmem[IO].unsafeRunSync()
    val history = History[IO](db).unsafeRunSync()
    history.init(config).unsafeRunSync()
    history
  }

  val accounts: mutable.Map[String, KeyPair] = mutable.Map.empty

  def address(account: String): Address = {
    if (!accounts.contains(account)) {
      accounts += (account -> SecP256k1.generateKeyPair().unsafeRunSync())
    }
    Address(accounts(account))
  }

  "state" should {
    "preprepared" in {
      implicit val cs = IO.contextShift(global)
      implicit val timer = IO.timer(global)

      val signers  = List("A", "B","C").map(address(_))
      val backlogs = Ref.unsafe[IO, MMap[Address, ArrayBuffer[Message]]](MMap.empty)
      val lruMap   = new LruMap[ByteVector, Snapshot](100)
      val pk       = accounts.get("A").get
      val current =
        Ref.unsafe[IO, RoundState](RoundState(0, 1, None, MessageSet.empty, MessageSet.empty, ByteVector.empty, false))
      val roundChanges = Ref.unsafe[IO, MMap[BigInt, MessageSet]](MMap.empty)
      val validatorSet = Ref.unsafe[IO, ValidatorSet](ValidatorSet(address("A"),ArrayBuffer.empty ++ signers))
      val promise      = Ref.unsafe[IO, Deferred[IO, BigInt]](Deferred[IO, BigInt].unsafeRunSync())
      val state        = Ref.unsafe[IO, State](StateNewRound)
      val istanbul = Istanbul[IO](IstanbulConfig(),
                                  mkHistory(signers),
                                  lruMap,
                                  MMap.empty,
                                  backlogs,
                                  pk,
                                  current,
                                  roundChanges,
                                  validatorSet,
                                  promise,
                                  state)
      istanbul.startNewRound(0)
    }
  }
}
