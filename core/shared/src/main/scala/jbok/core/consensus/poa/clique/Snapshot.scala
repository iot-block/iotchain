package jbok.core.consensus.poa.clique

import cats.effect.{Async, IO, Sync}
import _root_.io.circe._
import _root_.io.circe.generic.JsonCodec
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import cats.data.OptionT
import jbok.codec.json.implicits._
import jbok.codec.rlp.implicits._
import jbok.core.consensus.poa.clique.Clique._
import jbok.core.consensus.poa.clique.Snapshot._
import jbok.core.models.{Address, BlockHeader}
import jbok.persistent.KeyValueDB
import scodec.bits._
import scalacache.Cache
import cats.implicits._
import jbok.core.config.Configs.MiningConfig
import jbok.crypto.signature.KeyPair
import scalacache.CatsEffect.modes._
import scodec.Codec

@JsonCodec
final case class Vote(
    signer: Address, // Authorized signer that cast this vote
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
  * [[Snapshot]] is the state of the authorization voting at a given point(block hash and number).
  * the snapshot should be `immutable` once it has been created
  */
final case class Snapshot(
    config: MiningConfig,
    number: BigInt, // Block number where the snapshot was created
    hash: ByteVector, // Block hash where the snapshot was created
    signers: Set[Address], // Set of authorized signers at this moment
    recents: Map[BigInt, Address] = Map.empty, // Set of recent signers for spam protections
    votes: List[Vote] = Nil, // List of votes cast in chronological order
    tally: Map[Address, Tally] = Map.empty // Current vote tally to avoid recalculating
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

  implicit val ee: Encoder[Either[Address, KeyPair]] =
    Encoder.encodeEither[Address, KeyPair]("left", "right")

  implicit val ed: Decoder[Either[Address, KeyPair]] =
    Decoder.decodeEither[Address, KeyPair]("left", "right")

  implicit val miningConfigJsonEncoder: Encoder[MiningConfig] = deriveEncoder[MiningConfig]

  implicit val miningConfigJsonDecoder: Decoder[MiningConfig] = deriveDecoder[MiningConfig]

  implicit val snapshotJsonEncoder: Encoder[Snapshot] = deriveEncoder[Snapshot]

  implicit val snapshotJsonDecoder: Decoder[Snapshot] = deriveDecoder[Snapshot]

  implicit val byteArrayOrd: Ordering[Array[Byte]] = Ordering.by((_: Array[Byte]).toIterable)

  implicit val addressOrd: Ordering[Address] = Ordering.by(_.bytes.toArray)

  def storeSnapshot[F[_]: Async](snapshot: Snapshot, db: KeyValueDB[F], checkpointInterval: Int)(
      implicit C: Cache[Snapshot]): F[Unit] =
    if (snapshot.number % checkpointInterval == 0) {
      db.put(snapshot.hash, snapshot.asJson.noSpaces, namespace) <* C.put[F](snapshot.hash)(snapshot)
    } else {
      C.put[F](snapshot.hash)(snapshot).void
    }

  def loadSnapshot[F[_]](db: KeyValueDB[F], hash: ByteVector)(implicit F: Async[F],
                                                              C: Cache[Snapshot]): F[Option[Snapshot]] =
    C.get[F](hash).flatMap {
      case Some(snap) => Sync[F].pure(snap.some)
      case None =>
        (for {
          str  <- db.getOptT[ByteVector, String](hash, namespace)
          snap <- OptionT.fromOption[F](decode[Snapshot](str).toOption)
          _    <- OptionT.liftF(C.put[F](hash)(snap))
        } yield snap).value
    }

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
    val extra       = RlpCodec.decode[CliqueExtra](header.extra.bits).require.value
    val beneficiary = Address(header.beneficiary)

    // Resolve the authorization key and check against signers
    val signerOpt = Clique.ecrecover[IO](header).unsafeRunSync()
    signerOpt match {
      case None =>
      case Some(s) if !snap.signers.contains(s) =>
        throw new Exception("unauthorized signer")
      case _ => ()
    }

    val Some(signer) = signerOpt

    if (snap.recents.exists(_._2 == signer)) {
      throw new Exception("signer has signed recently")
    }

    // Tally up the new vote from the signer
    val authorize = extra.auth

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
