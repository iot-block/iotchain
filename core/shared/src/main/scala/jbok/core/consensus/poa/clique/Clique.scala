package jbok.core.consensus.poa.clique

import cats.data.OptionT
import cats.effect.{ConcurrentEffect, Sync}
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.config.Configs.MiningConfig
import jbok.core.config.GenesisConfig
import jbok.core.consensus.Extra
import jbok.core.consensus.poa.clique.Clique._
import jbok.core.ledger.History
import jbok.core.models._
import jbok.crypto._
import jbok.crypto.signature._
import jbok.persistent.{CacheBuilder, DBErr}
import scalacache._
import scodec.bits._

import scala.concurrent.duration._

final class Clique[F[_]](
    val config: MiningConfig,
    val history: History[F],
    val proposals: Map[Address, Boolean], // Current list of proposals we are pushing
    val keyPair: Option[KeyPair]
)(implicit F: ConcurrentEffect[F], C: Cache[Snapshot]) {
  private[this] val log = jbok.common.log.getLogger("Clique")

  import config._

  lazy val signer: Address = keyPair match {
    case Some(kp) => Address(kp)
    case None     => throw new Exception("no signer keyPair defined")
  }

  def sign(bv: ByteVector): F[CryptoSignature] =
    keyPair match {
      case Some(kp) => Signature[ECDSA].sign[F](bv.toArray, kp, history.chainId)
      case None     => F.raiseError(new Exception("no signer keyPair defined"))
    }

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
          newSnap <- Snapshot.applyHeaders[F](s, headers, history.chainId)
          _       <- Snapshot.storeSnapshot[F](newSnap, history.db, checkpointInterval)
        } yield newSnap

      case None =>
        // No snapshot for this header, gather the header and move backward(recur)
        for {
          (h, p) <- parents.lastOption match {
            case Some(last) =>
              // If we have explicit parents, pick from there (enforced)
              F.pure((last, parents.slice(0, parents.length - 1)))
            case None =>
              // No explicit parents (or no more left), reach out to the database
              history.getBlockHeaderByHash(hash).flatMap {
                case Some(header) => F.pure(header -> parents)
                case None         => ???
              }
          }
          snap <- applyHeaders(number - 1, h.parentHash, p, h :: headers)
        } yield snap
    }
  }

  private def genesisSnapshot: F[Snapshot] = {
    log.trace(s"making a genesis snapshot")
    for {
      genesis <- history.genesisHeader
      _     = log.debug(s"decode genesis extra ${genesis.extra}")
      extra = RlpCodec.decode[CliqueExtra](genesis.extra.bits).require.value
      snap  = Snapshot(config, 0, genesis.hash, extra.signer.toSet)
      _ <- Snapshot.storeSnapshot[F](snap, history.db, checkpointInterval)
      _ = log.trace(s"stored genesis with ${extra.signer.size} signers")
    } yield snap
  }
}

object Clique {
  sealed trait CliqueAlgo
  object CliqueAlgo extends CliqueAlgo
  final case class CliqueExtra(signer: List[Address], signature: CryptoSignature, auth: Boolean = false)
      extends Extra[CliqueAlgo]

  val extraVanity   = 32 // Fixed number of extra-data prefix bytes reserved for signer vanity
  val extraSeal     = 65 // Fixed number of extra-data suffix bytes reserved for signer seal
  val ommersHash    = List.empty[BlockHeader].asBytes.kec256 // Always Keccak256(RLP([])) as uncles are meaningless outside of PoW.
  val diffInTurn    = BigInt(11) // Block difficulty for in-turn signatures
  val diffNoTurn    = BigInt(10) // Block difficulty for out-of-turn signatures
  val nonceAuthVote = hex"0xffffffffffffffff" // Magic nonce number to vote on adding a new signer
  val nonceDropVote = hex"0x0000000000000000" // Magic nonce number to vote on removing a signer.

  val inMemorySnapshots: Int     = 128
  val inMemorySignatures: Int    = 1024
  val wiggleTime: FiniteDuration = 500.millis

  def apply[F[_]](
      config: MiningConfig,
      genesisConfig: GenesisConfig,
      history: History[F],
      keyPair: Option[KeyPair]
  )(implicit F: ConcurrentEffect[F]): F[Clique[F]] =
    for {
      genesisBlock <- history.getBlockByNumber(0)
      _ <- if (genesisBlock.isEmpty) {
        history.initGenesis(genesisConfig)
      } else {
        F.unit
      }
      cache <- CacheBuilder.build[F, Snapshot](inMemorySnapshots)
    } yield new Clique[F](config, history, Map.empty, keyPair)(F, cache)

  private[clique] def fillExtraData(signers: List[Address]): ByteVector =
    CliqueExtra(signers, CryptoSignature(ByteVector.fill(65)(0.toByte).toArray)).asBytes
//    ByteVector.fill(extraVanity)(0.toByte) ++ signers.foldLeft(ByteVector.empty)(_ ++ _.bytes) ++ ByteVector.fill(
//      extraSeal)(0.toByte)

  def sigHash[F[_]](header: BlockHeader)(implicit F: Sync[F]): F[ByteVector] = F.delay {
    val bytes = header.copy(extra = ByteVector.empty).asBytes
//    val bytes = RlpCodec.encode(header.copy(extraData = header.extraData.dropRight(extraSeal))).require.bytes
    bytes.kec256
  }

  /** Retrieve the signature from the header extra-data */
  def ecrecover[F[_]](header: BlockHeader, chainId: BigInt)(implicit F: Sync[F]): F[Option[Address]] =
    for {
      extra <- F.delay(RlpCodec.decode[CliqueExtra](header.extra.bits).require.value)
      sig = extra.signature
      hash <- sigHash[F](header)
    } yield {
      Signature[ECDSA].recoverPublic(hash.toArray, sig, chainId).map(pub => Address(pub.bytes.kec256))
    }

  def generateGenesisConfig(template: GenesisConfig, signers: List[Address]): GenesisConfig =
    template.copy(
      extraData = Clique.fillExtraData(signers),
      timestamp = System.currentTimeMillis()
    )
}
