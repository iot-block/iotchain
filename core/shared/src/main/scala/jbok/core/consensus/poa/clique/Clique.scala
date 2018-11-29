package jbok.core.consensus.poa.clique

import cats.data.OptionT
import cats.effect.ConcurrentEffect
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.implicits._
import jbok.core.consensus.poa.clique.Clique._
import jbok.core.ledger.History
import jbok.core.models._
import jbok.crypto._
import jbok.crypto.signature.{CryptoSignature, ECDSA, KeyPair, Signature}
import scalacache._
import scodec.bits._
import _root_.io.circe.generic.JsonCodec
import jbok.codec.json.implicits._
import jbok.persistent.CacheBuilder

import scala.concurrent.duration._

@JsonCodec
case class CliqueConfig(
    period: FiniteDuration = 15.seconds, // Number of seconds between blocks to enforce
    epoch: BigInt = BigInt(30000), // Epoch length to reset votes and checkpoint
    checkpointInterval: Int = 1024, // Number of blocks after which to save the vote snapshot to the database
    inMemorySnapshots: Int = 128, // Number of recent vote snapshots to keep in memory
    inMemorySignatures: Int = 1024, // Number of recent blocks to keep in memory
    wiggleTime: FiniteDuration = 500.millis // Random delay (per signer) to allow concurrent signers
)

class Clique[F[_]](
    val config: CliqueConfig,
    val history: History[F],
    val proposals: Map[Address, Boolean], // Current list of proposals we are pushing
    val keyPair: KeyPair
)(implicit F: ConcurrentEffect[F], C: Cache[Snapshot]) {
  private[this] val log = org.log4s.getLogger("Clique")

  import config._

  val signer: Address = Address(keyPair)

  def sign(bv: ByteVector): F[CryptoSignature] = F.liftIO(Signature[ECDSA].sign(bv.toArray, keyPair))

  def applyHeaders(
      number: BigInt,
      hash: ByteVector,
      parents: List[BlockHeader],
      headers: List[BlockHeader] = Nil
  ): F[Snapshot] = {
    val snap =
      OptionT(Snapshot.loadSnapshot[F](history.db, hash))
        .orElseF(if (number == 0) genesisSnapshot.map(_.some) else F.pure(None))

    snap.value flatMap {
      case Some(s) =>
        // Previous snapshot found, apply any pending headers on top of it
        log.trace(s"applying ${headers.length} headers")
        for {
          newSnap <- Snapshot.applyHeaders[F](s, headers)
          _       <- Snapshot.storeSnapshot[F](newSnap, history.db, checkpointInterval)
        } yield newSnap

      case None =>
        // No snapshot for this header, gather the header and move backward(recur)
        for {
          (h, p) <- if (parents.nonEmpty) {
            // If we have explicit parents, pick from there (enforced)
            F.pure((parents.last, parents.slice(0, parents.length - 1)))
          } else {
            // No explicit parents (or no more left), reach out to the database
            history.getBlockHeaderByHash(hash).map(header => header.get -> parents)
          }
          snap <- applyHeaders(number - 1, h.parentHash, p, h :: headers)
        } yield snap
    }
  }

  private def genesisSnapshot: F[Snapshot] = {
    log.trace(s"making a genesis snapshot")
    for {
      genesis <- history.genesisHeader
      n = (genesis.extraData.length - extraVanity - extraSeal).toInt / 20
      signers: Set[Address] = (0 until n)
        .map(i => Address(genesis.extraData.slice(i * 20 + extraVanity, i * 20 + extraVanity + 20)))
        .toSet
      snap = Snapshot(config, 0, genesis.hash, signers)
      _ <- Snapshot.storeSnapshot[F](snap, history.db, checkpointInterval)
      _ = log.trace(s"stored genesis with ${signers.size} signers")
    } yield snap
  }
}

object Clique {
  val extraVanity = 32 // Fixed number of extra-data prefix bytes reserved for signer vanity
  val extraSeal   = 65 // Fixed number of extra-data suffix bytes reserved for signer seal
  val ommersHash = RlpCodec
    .encode(List.empty[BlockHeader])
    .require
    .bytes
    .kec256 // Always Keccak256(RLP([])) as uncles are meaningless outside of PoW.
  val diffInTurn    = BigInt(11)              // Block difficulty for in-turn signatures
  val diffNoTurn    = BigInt(10)              // Block difficulty for out-of-turn signatures
  val nonceAuthVote = hex"0xffffffffffffffff" // Magic nonce number to vote on adding a new signer
  val nonceDropVote = hex"0x0000000000000000" // Magic nonce number to vote on removing a signer.

  def apply[F[_]](
      config: CliqueConfig,
      history: History[F],
      keyPair: KeyPair
  )(implicit F: ConcurrentEffect[F]): F[Clique[F]] =
    for {
      cache <- CacheBuilder.build[F, Snapshot](config.inMemorySnapshots)
    } yield new Clique[F](config, history, Map.empty, keyPair)(F, cache)

  def fillExtraData(signers: List[Address]): ByteVector =
    ByteVector.fill(extraVanity)(0.toByte) ++ signers.foldLeft(ByteVector.empty)(_ ++ _.bytes) ++ ByteVector.fill(
      extraSeal)(0.toByte)

  def sigHash(header: BlockHeader): ByteVector = {
    val bytes = RlpCodec.encode(header.copy(extraData = header.extraData.dropRight(extraSeal))).require.bytes
    bytes.kec256
  }

  def ecrecover(header: BlockHeader): Option[Address] = {
    // Retrieve the signature from the header extra-data
    val signature = header.extraData.takeRight(extraSeal)
    val hash      = sigHash(header)
    val sig       = CryptoSignature(signature.toArray)
    Signature[ECDSA]
      .recoverPublic(hash.toArray, sig, None)
      .map(pub => Address(pub.bytes.kec256))
  }
}
