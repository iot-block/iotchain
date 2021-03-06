package jbok.core.consensus.poa.clique

import cats.data.OptionT
import cats.effect.concurrent.Ref
import cats.effect.{Concurrent, Sync}
import cats.implicits._
import jbok.codec.rlp.RlpEncoded
import jbok.codec.rlp.implicits._
import jbok.common.log.Logger
import jbok.common.math.N
import jbok.common.math.implicits._
import jbok.core.config.{GenesisConfig, MiningConfig}
import jbok.core.keystore.KeyStore
import jbok.core.ledger.History
import jbok.core.models._
import jbok.core.store.ColumnFamilies
import jbok.crypto._
import jbok.crypto.signature._
import jbok.persistent.{KVStore, SingleColumnKVStore}
import scodec.bits._

import scala.concurrent.duration._

final case class Proposal(address: Address, auth: Boolean)

final case class CliqueExtra(miners: List[Address], signature: CryptoSignature, proposal: Option[Proposal] = None)

final class Clique[F[_]](
    config: MiningConfig,
    store: SingleColumnKVStore[F, ByteVector, String],
    history: History[F],
    proposal: Ref[F, Option[Proposal]],
    keyPair: KeyPair
)(implicit F: Concurrent[F]) {
  private[this] val log = Logger[F]

  val minerAddress: Address = Address(keyPair)

  def sign(bv: ByteVector): F[CryptoSignature] =
    Signature[ECDSA].sign[F](bv.toArray, keyPair, history.chainId.value.toBigInt)

  def fillExtraData(header: BlockHeader): F[BlockHeader] =
    for {
      bytes      <- Clique.sigHash[F](header)
      signed     <- sign(bytes)
      extraBytes <- proposal.get.map(CliqueExtra(Nil, signed, _).encoded)
    } yield header.copy(extra = extraBytes)

  def clearProposalIfMine(header: BlockHeader): F[Unit] =
    for {
      extra       <- F.fromEither(header.extraAs[CliqueExtra])
      bytes       <- Clique.sigHash[F](header)
      mySigned    <- sign(bytes)
      proposalOpt <- proposal.get
      _ <- if (extra.signature == mySigned && extra.proposal == proposalOpt) {
        proposal.update(_ => None)
      } else {
        F.unit
      }
    } yield ()

  def ballot(address: Address, auth: Boolean): F[Unit] =
    proposal.update(_ => Some(Proposal(address, auth)))

  def cancelBallot: F[Unit] = proposal.update(_ => None)

  def getProposal: F[Option[Proposal]] = proposal.get

  def applyHeaders(
      number: N,
      hash: ByteVector,
      parents: List[BlockHeader],
      headers: List[BlockHeader] = Nil
  ): F[Snapshot] = {
    val snap =
      OptionT(Snapshot.loadSnapshot[F](store, hash))
        .orElseF(if (number == 0) genesisSnapshot.map(_.some) else F.pure(None))

    snap.value flatMap {
      case Some(s) =>
        // Previous snapshot found, apply any pending headers on top of it
        for {
          newSnap <- Snapshot.applyHeaders[F](s, headers, history.chainId)
          _       <- Snapshot.storeSnapshot[F](newSnap, store)
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
      extra   <- F.fromEither(genesis.extraAs[CliqueExtra])
      snap = Snapshot(config, 0, genesis.hash, extra.miners.toSet)
      _ <- Snapshot.storeSnapshot[F](snap, store)
      _ <- log.i(s"stored genesis with ${extra.miners.size} miners")
    } yield snap
}

object Clique {
  val diffInTurn = N(11) // Block difficulty for in-turn signatures
  val diffNoTurn = N(10) // Block difficulty for out-of-turn signatures

  val inMemorySnapshots: Int     = 128
  val inMemorySignatures: Int    = 1024
  val wiggleTime: FiniteDuration = 500.millis

  def apply[F[_]](
      config: MiningConfig,
      genesisConfig: GenesisConfig,
      db: KVStore[F],
      history: History[F],
      keystore: KeyStore[F]
  )(implicit F: Concurrent[F]): F[Clique[F]] =
    for {
      keyPair      <- keystore.unlockAccount(config.address, config.passphrase).map(_.keyPair)
      genesisBlock <- history.getBlockByNumber(0)
      _ <- if (genesisBlock.isEmpty) {
        history.initGenesis(genesisConfig)
      } else {
        F.unit
      }
      store = SingleColumnKVStore[F, ByteVector, String](ColumnFamilies.Snapshot, db)
      proposal <- Ref[F].of(Option.empty[Proposal])
    } yield new Clique[F](config, store, history, proposal, keyPair)

  def fillExtraData(miners: List[Address]): RlpEncoded =
    CliqueExtra(miners, CryptoSignature(ByteVector.fill(65)(0.toByte).toArray)).encoded

  def sigHash[F[_]](header: BlockHeader)(implicit F: Sync[F]): F[ByteVector] = F.delay {
    val encoded = header.copy(extra = RlpEncoded.emptyList).encoded
    encoded.bytes.kec256
  }

  /** Retrieve the signature from the header extra-data */
  def ecrecover[F[_]](header: BlockHeader, chainId: ChainId)(implicit F: Sync[F]): F[Option[Address]] =
    for {
      extra <- F.fromEither(header.extraAs[CliqueExtra])
      sig = extra.signature
      hash <- sigHash[F](header)
    } yield {
      Signature[ECDSA].recoverPublic(hash.toArray, sig, chainId.value.toBigInt).map(pub => Address(pub.bytes.kec256))
    }
}
