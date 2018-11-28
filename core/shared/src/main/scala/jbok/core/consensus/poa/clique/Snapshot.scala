package jbok.core.consensus.poa.clique

import cats.effect.Sync
import cats.implicits._
import io.circe._
import io.circe.generic.JsonCodec
import io.circe.parser._
import io.circe.syntax._
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.consensus.poa.clique.Clique._
import jbok.core.consensus.poa.clique.Snapshot._
import jbok.core.models.{Address, BlockHeader}
import jbok.persistent.KeyValueDB
import scodec.bits._

@JsonCodec
case class Vote(
    signer: Address, // Authorized signer that cast this vote
    block: BigInt, // Block number the vote was cast in (expire old votes)
    address: Address, // Account being voted on to change its authorization
    authorize: Boolean // Whether to authorize or deauthorize the voted account
)

@JsonCodec
case class Tally(
    authorize: Boolean, // Whether the vote is about authorizing or kicking someone
    votes: Int // Number of votes until now wanting to pass the proposal
)

/**
  * [[Snapshot]] is the state of the authorization voting at a given point(block hash and number).
  * the snapshot should be `immutable` once it has been created
  */
case class Snapshot(
    config: CliqueConfig,
    number: BigInt, // Block number where the snapshot was created
    hash: ByteVector, // Block hash where the snapshot was created
    signers: Set[Address], // Set of authorized signers at this moment
    recents: Map[BigInt, Address], // Set of recent signers for spam protections
    votes: List[Vote], // List of votes cast in chronological order
    tally: Map[Address, Tally] // Current vote tally to avoid recalculating
) {

  /** cast adds a new vote into the tally. should clear previous votes from signer -> beneficiary */
  def cast(signer: Address, beneficiary: Address, authorize: Boolean): Snapshot = {
    val dedup: Snapshot =
      votes
        .filter(x => x.signer == signer && x.address == beneficiary)
        .foldLeft(this)((snap, v) => snap.uncast(v.address, v.authorize))
        .copy(votes = votes.filterNot(x => x.signer == signer && x.address == beneficiary))

    dedup.signers.contains(beneficiary) match {
      case true if authorize   => dedup
      case false if !authorize => dedup
      case _ =>
        val vote = Vote(signer, number, beneficiary, authorize)
        if (dedup.tally.contains(beneficiary)) {
          val old = dedup.tally(beneficiary)
          dedup.copy(
            tally = dedup.tally + (beneficiary -> old.copy(votes = old.votes + 1)),
            votes = dedup.votes ++ List(vote)
          )
        } else {
          dedup.copy(
            tally = dedup.tally + (beneficiary -> Tally(authorize, 1)),
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

  /** signers retrieves the list of authorized signers in ascending order. */
  def getSigners: List[Address] = signers.toList.sorted

  /** inturn returns if a signer at a given block height is in-turn or not. */
  def inturn(number: BigInt, signer: Address): Boolean = {
    val signers = getSigners
    val offset = signers.zipWithIndex.collectFirst {
      case (address, index) if address == signer => index
    }
    offset.exists(i => number % BigInt(signers.length) == BigInt(i))
  }

  def clearStaleVotes(number: BigInt): Snapshot =
    if (number % config.epoch == 0) {
      copy(votes = Nil, tally = Map.empty)
    } else {
      this
    }

  /** Delete the oldest signer from the recent list to allow it signing again */
  def deleteOldestRecent(number: BigInt): Snapshot = {
    val limit = BigInt(signers.size / 2 + 1)
    if (number >= limit) {
      copy(recents = this.recents - (number - limit))
    } else {
      this
    }
  }

  def authorized(beneficiary: Address): Snapshot =
    copy(
      signers = this.signers + beneficiary,
      tally = this.tally - beneficiary,
      votes = this.votes.filter(_.address != beneficiary)
    )

  def deauthorized(beneficiary: Address, number: BigInt): Snapshot = {
    // Signer list shrunk, delete any leftover recent caches
    val uncasted =
      votes
        .filter(_.signer == beneficiary)
        .foldLeft(this)((snap, v) => snap.uncast(v.address, v.authorize))

    uncasted
      .copy(
        signers = uncasted.signers - beneficiary,
        tally = uncasted.tally - beneficiary,
        votes = uncasted.votes.filter(x => x.address != beneficiary || x.signer != beneficiary)
      )
      .deleteOldestRecent(number)
  }
}

object Snapshot {
  val namespace = ByteVector("clique".getBytes)

  implicit val addressKeyEncoder =
    KeyEncoder.instance[Address](_.bytes.asJson.noSpaces)

  implicit val addressKeyDecoder =
    KeyDecoder.instance[Address](s => decode[ByteVector](s).map(bytes => Address(bytes)).right.toOption)

  implicit val bigIntKeyEncoder =
    KeyEncoder.instance[BigInt](_.asJson.noSpaces)

  implicit val bigIntKeyDecoder =
    KeyDecoder.instance[BigInt](s => decode[BigInt](s).right.toOption)

  implicit val snapshotJsonEncoder: Encoder[Snapshot] = deriveEncoder[Snapshot]

  implicit val snapshotJsonDecoder: Decoder[Snapshot] = deriveDecoder[Snapshot]

  implicit val byteArrayOrd: Ordering[Array[Byte]] = new Ordering[Array[Byte]] {
    def compare(a: Array[Byte], b: Array[Byte]): Int =
      if (a eq null) {
        if (b eq null) 0
        else -1
      } else if (b eq null) 1
      else {
        val L = math.min(a.length, b.length)
        var i = 0
        while (i < L) {
          if (a(i) < b(i)) return -1
          else if (b(i) < a(i)) return 1
          i += 1
        }
        if (L < b.length) -1
        else if (L < a.length) 1
        else 0
      }
  }

  implicit val addressOrd: Ordering[Address] = Ordering.by(_.bytes.toArray)

  def storeSnapshot[F[_]: Sync](snapshot: Snapshot, db: KeyValueDB[F]): F[Unit] =
    db.put(snapshot.hash, snapshot.asJson.noSpaces, namespace)

  def loadSnapshot[F[_]: Sync](db: KeyValueDB[F], hash: ByteVector): F[Option[Snapshot]] =
    db.getOpt[ByteVector, String](hash, namespace).map(_.map(json => io.circe.parser.decode[Snapshot](json).right.get))

  def apply(config: CliqueConfig, number: BigInt, hash: ByteVector, signers: Set[Address]): Snapshot =
    new Snapshot(config, number, hash, signers, Map.empty, List.empty, Map.empty)

  /** apply creates a new authorization snapshot by applying the given headers to the original one */
  def applyHeaders[F[_]](snapshot: Snapshot, headers: List[BlockHeader])(implicit F: Sync[F]): F[Snapshot] =
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
        Snapshot.applyHeader(cleared, header)
      })
    }

  /** create a new snapshot by applying a given header */
  private def applyHeader[F[_]](snap: Snapshot, header: BlockHeader)(implicit F: Sync[F]): F[Snapshot] = F.delay {
    val number      = header.number
    val beneficiary = Address(header.beneficiary)

    // Resolve the authorization key and check against signers
    val signer = Clique.ecrecover(header)
    if (!snap.signers.contains(signer)) {
      throw new Exception("unauthorized signer")
    }

    if (snap.recents.exists(_._2 == signer)) {
      throw new Exception("signer has signed recently")
    }

    // Tally up the new vote from the signer
    val authorize = if (header.nonce == nonceAuthVote) {
      true
    } else if (header.nonce == nonceDropVote) {
      false
    } else {
      throw new Exception("invalid vote")
    }

    val casted = snap.cast(signer, beneficiary, authorize)

    // If the vote passed, update the list of signers
    val result = casted.tally.get(beneficiary) match {
      case Some(t) if t.votes > snap.signers.size / 2 && t.authorize =>
        casted.authorized(beneficiary)

      case Some(t) if t.votes > snap.signers.size / 2 =>
        casted.deauthorized(beneficiary, number)

      case _ =>
        casted
    }

    result.copy(
      number = snap.number + 1,
      hash = header.hash,
      recents = result.recents + (number -> signer)
    )
  }
}
