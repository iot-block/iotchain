package jbok.core.consensus.poa.clique

import cats.effect.{Async, Sync}
import _root_.io.circe.generic.JsonCodec
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import cats.data.OptionT
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.consensus.poa.clique.Snapshot._
import jbok.core.models.{Address, BlockHeader}
import jbok.persistent.SingleColumnKVStore
import scodec.bits._
import cats.implicits._
import jbok.core.config.MiningConfig

@JsonCodec
final case class Vote(
    miner: Address, // Authorized miner that cast this vote
    block: BigInt, // Block number the vote was cast in (expire old votes)
    address: Address, // Account being voted on to change its authorization
    authorize: Boolean // Whether to authorize or deauthorize the voted account
)

@JsonCodec
final case class Tally(
    authorize: Boolean, // Whether the vote is about authorizing or kicking someone
    votes: Int // Number of votes until now wanting to pass the proposal
)

/**
  * `Snapshot` is the state of the authorization voting at a given point(block hash and number).
  * the snapshot should be *immutable* once it has been created
  */
@JsonCodec
final case class Snapshot(
    config: MiningConfig,
    number: BigInt, // Block number where the snapshot was created
    hash: ByteVector, // Block hash where the snapshot was created
    miners: Set[Address], // Set of authorized miners at this moment
    recents: Map[BigInt, Address] = Map.empty, // Set of recent miners for spam protections
    votes: List[Vote] = Nil, // List of votes cast in chronological order
    tally: Map[Address, Tally] = Map.empty // Current vote tally to avoid recalculating
) {

  /** cast adds a new vote into the tally. should clear previous votes from miner -> address */
  def cast(miner: Address, address: Address, authorize: Boolean): Snapshot = {
    val dedup: Snapshot =
      votes
        .filter(x => x.miner == miner && x.address == address)
        .foldLeft(this)((snap, v) => snap.uncast(v.address, v.authorize))
        .copy(votes = votes.filterNot(x => x.miner == miner && x.address == address))

    dedup.miners.contains(address) match {
      case true if authorize   => dedup
      case false if !authorize => dedup
      case _ =>
        val vote = Vote(miner, number, address, authorize)
        if (dedup.tally.contains(address)) {
          val old = dedup.tally(address)
          dedup.copy(
            tally = dedup.tally + (address -> old.copy(votes = old.votes + 1)),
            votes = dedup.votes ++ List(vote)
          )
        } else {
          dedup.copy(
            tally = dedup.tally + (address -> Tally(authorize, 1)),
            votes = dedup.votes ++ List(vote)
          )
        }
    }
  }

  /** uncast removes a previously cast vote from the tally. */
  def uncast(address: Address, authorize: Boolean): Snapshot =
    tally.get(address) match {
      case None                                => this // If there's no tally, it's a dangling vote, just drop
      case Some(t) if t.authorize != authorize => this // Ensure we only revert counted votes
      case Some(t) =>
        if (t.votes > 1) {
          copy(tally = this.tally + (address -> t.copy(votes = t.votes - 1)))
        } else {
          copy(tally = this.tally - address)
        }
    }

  def sortedMiners: List[Address] = miners.toList.sorted

  /** inturn returns if a miner at a given block height is in-turn or not. */
  def inturn(number: BigInt, miner: Address): Boolean = {
    val miners = sortedMiners
    val offset = miners.zipWithIndex.collectFirst {
      case (address, index) if address == miner => index
    }
    offset.exists(i => number % BigInt(miners.length) == BigInt(i))
  }

  def clearStaleVotes(number: BigInt): Snapshot =
    if (number % config.epoch == 0) {
      copy(votes = Nil, tally = Map.empty)
    } else {
      this
    }

  /** Delete the oldest miner from the recent list to allow it signing again */
  def deleteOldestRecent(number: BigInt): Snapshot = {
    val limit = BigInt(miners.size / 2 + 1)
    if (number >= limit) {
      copy(recents = this.recents - (number - limit))
    } else {
      this
    }
  }

  def authorized(address: Address): Snapshot =
    copy(
      miners = this.miners + address,
      tally = this.tally - address,
      votes = this.votes.filter(_.address != address)
    )

  def deauthorized(address: Address, number: BigInt): Snapshot = {
    // miner list shrunk, delete any leftover recent caches
    val uncasted =
      votes
        .filter(_.miner == address)
        .foldLeft(this)((snap, v) => snap.uncast(v.address, v.authorize))

    uncasted
      .copy(
        miners = uncasted.miners - address,
        tally = uncasted.tally - address,
        votes = uncasted.votes.filter(x => x.address != address || x.miner != address)
      )
      .deleteOldestRecent(number)
  }
}

object Snapshot {
  implicit val byteArrayOrd: Ordering[Array[Byte]] = Ordering.by((_: Array[Byte]).toIterable)

  implicit val addressOrd: Ordering[Address] = Ordering.by(_.bytes.toArray)

  def storeSnapshot[F[_]: Async](snapshot: Snapshot, store: SingleColumnKVStore[F, ByteVector, String]): F[Unit] =
    store.put(snapshot.hash, snapshot.asJson.noSpaces)

  def loadSnapshot[F[_]](store: SingleColumnKVStore[F, ByteVector, String], hash: ByteVector)(implicit F: Sync[F]): F[Option[Snapshot]] =
    (for {
      str  <- OptionT(store.get(hash))
      snap <- OptionT.fromOption[F](decode[Snapshot](str).toOption)
    } yield snap).value

  /** apply creates a new authorization snapshot by applying the given headers to the original one */
  def applyHeaders[F[_]](snapshot: Snapshot, headers: List[BlockHeader], chainId: BigInt)(implicit F: Sync[F]): F[Snapshot] =
    if (headers.isEmpty) {
      snapshot.pure[F]
    } else {
      // sanity check that the headers can be applied
      if (headers.sliding(2).exists {
            case left :: right :: Nil => left.number + 1 != right.number
            case _                    => false
          }) {
        F.raiseError(new Exception("invalid voting chain"))
      }

      if (headers.head.number != snapshot.number + 1) {
        F.raiseError(new Exception("invalid voting chain"))
      }

      headers.foldLeftM(snapshot)((snap, header) => {
        val cleared = snap.clearStaleVotes(header.number).deleteOldestRecent(header.number)
        Snapshot.applyHeader(cleared, header, chainId)
      })
    }

  /** create a new snapshot by applying a given header */
  private def applyHeader[F[_]](snap: Snapshot, header: BlockHeader, chainId: BigInt)(implicit F: Sync[F]): F[Snapshot] = {
    val number = header.number
    val extra  = RlpCodec.decode[CliqueExtra](header.extra.bits).require.value
    Clique.ecrecover[F](header, chainId).map {
      case None                                              => throw new Exception("recover none from signature")
      case Some(miner) if !snap.miners.contains(miner)       => throw new Exception("unauthorized miner")
      case Some(miner) if snap.recents.exists(_._2 == miner) => throw new Exception("miner has mined recently")
      case Some(miner)                                       =>
        // Tally up the new vote from the miner
        val authorize = extra.proposal.exists(_.auth == true)
        val address   = extra.proposal.map(_.address).getOrElse(Address.empty)
        val casted = snap.cast(miner, address, authorize)

        // If the vote passed, update the list of miners
        val result = casted.tally.get(address) match {
          case Some(t) if t.votes > snap.miners.size / 2 && t.authorize =>
            casted.authorized(address)

          case Some(t) if t.votes > snap.miners.size / 2 =>
            casted.deauthorized(address, number)

          case _ =>
            casted
        }

        result.copy(
          number = snap.number + 1,
          hash = header.hash,
          recents = result.recents + (number -> miner)
        )
    }
  }
}
