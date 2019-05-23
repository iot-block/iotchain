package jbok.core.consensus.poa.clique

import cats.data.OptionT
import cats.effect.{ConcurrentEffect, IO, Sync}
import cats.implicits._
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.core.config.MiningConfig
import jbok.core.config.GenesisConfig
import jbok.core.ledger.History
import jbok.core.models._
import jbok.crypto._
import jbok.crypto.signature._
import jbok.persistent.KeyValueDB
import scodec.bits._

import scala.concurrent.duration._

final case class CliqueExtra(miners: List[Address], signature: CryptoSignature, auth: Boolean = false)

final class Clique[F[_]](
    config: MiningConfig,
    db: KeyValueDB[F],
    history: History[F],
    proposals: Map[Address, Boolean] = Map.empty
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = Logger[F]

  import config._

  val keyPair: KeyPair = {
    val secret = KeyPair.Secret(config.secret)
    val public = Signature[ECDSA].generatePublicKey[IO](secret).unsafeRunSync()
    KeyPair(public, secret)
  }

  val minerAddress: Address = Address(keyPair)

  def sign(bv: ByteVector): F[CryptoSignature] =
    Signature[ECDSA].sign[F](bv.toArray, keyPair, history.chainId)

  def applyHeaders(
      number: BigInt,
      hash: ByteVector,
      parents: List[BlockHeader],
      headers: List[BlockHeader] = Nil
  ): F[Snapshot] = {
    val snap =
      OptionT(Snapshot.loadSnapshot[F](db, hash))
        .orElseF(if (number == 0) genesisSnapshot.map(_.some) else F.pure(None))

    snap.value flatMap {
      case Some(s) =>
        // Previous snapshot found, apply any pending headers on top of it
        for {
          newSnap <- Snapshot.applyHeaders[F](s, headers, history.chainId)
          _       <- Snapshot.storeSnapshot[F](newSnap, db, checkpointInterval)
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

  private def genesisSnapshot: F[Snapshot] =
    for {
      genesis <- history.genesisHeader
      extra   <- genesis.extraAs[F, CliqueExtra]
      snap = Snapshot(config, 0, genesis.hash, extra.miners.toSet)
      _ <- Snapshot.storeSnapshot[F](snap, db, checkpointInterval)
      _ <- log.i(s"stored genesis with ${extra.miners.size} miners")
    } yield snap
}

object Clique {
  val diffInTurn = BigInt(11) // Block difficulty for in-turn signatures
  val diffNoTurn = BigInt(10) // Block difficulty for out-of-turn signatures

  val wiggleTime: FiniteDuration = 500.millis

  def apply[F[_]](
      config: MiningConfig,
      db: KeyValueDB[F],
      genesisConfig: GenesisConfig,
      history: History[F],
  )(implicit F: ConcurrentEffect[F]): F[Clique[F]] =
    for {
      genesisBlock <- history.getBlockByNumber(0)
      _ <- if (genesisBlock.isEmpty) {
        history.initGenesis(genesisConfig)
      } else {
        F.unit
      }
    } yield new Clique[F](config, db, history, Map.empty)

  def fillExtraData(miners: List[Address]): ByteVector =
    CliqueExtra(miners, CryptoSignature(ByteVector.fill(65)(0.toByte).toArray)).asValidBytes

  def sigHash[F[_]](header: BlockHeader)(implicit F: Sync[F]): F[ByteVector] = F.delay {
    val bytes = header.copy(extra = ByteVector.empty).asValidBytes
    bytes.kec256
  }

  /** Retrieve the signature from the header extra-data */
  def ecrecover[F[_]](header: BlockHeader, chainId: BigInt)(implicit F: Sync[F]): F[Option[Address]] =
    for {
      extra <- header.extraAs[F, CliqueExtra]
      sig = extra.signature
      hash <- sigHash[F](header)
    } yield {
      Signature[ECDSA].recoverPublic(hash.toArray, sig, chainId).map(pub => Address(pub.bytes.kec256))
    }
}
