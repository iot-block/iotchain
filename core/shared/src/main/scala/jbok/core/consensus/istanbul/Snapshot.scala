package jbok.core.consensus.istanbul

import cats.effect.{Async, Sync}
import cats.implicits._
import _root_.io.circe._
import _root_.io.circe.generic.JsonCodec
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import jbok.codec.json.implicits._
import jbok.core.models.{Address, BlockHeader}
import jbok.persistent.KeyValueDB
import jbok.core.consensus.istanbul.Snapshot._
import scodec.bits._
import jbok.codec.rlp.implicits._
import scalacache.Cache
import scalacache.CatsEffect.modes._

import scala.collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}

// Vote represents a single vote that an authorized signer made to modify the
// list of authorizations.
@JsonCodec
case class Vote(
    signer: Address, // Authorized signer that cast this vote
    block: BigInt, // Block number the vote was cast in (expire old votes)
    address: Address, // Account being voted on to change its authorization
    authorize: Boolean // Whether to authorize or deauthorize the voted account
)

// Tally is a simple vote tally to keep the current score of votes. Votes that
// go against the proposal aren't counted since it's equivalent to not voting.
@JsonCodec
case class Tally(
    authorize: Boolean, // Whether the vote is about authorizing or kicking someone
    votes: Int // Number of votes until now wanting to pass the proposal
)

case class Snapshot(
    config: IstanbulConfig,
    number: BigInt, // Block number where the snapshot was created
    hash: ByteVector, // Block hash where the snapshot was created
    validatorSet: ValidatorSet, // Set of authorized validators at this moment
    votes: ArrayBuffer[Vote], // List of votes cast in chronological order
    tally: MMap[Address, Tally] // Current vote tally to avoid recalculating
) {
  // cast adds a new vote into the tally.
  def cast(address: Address, authorize: Boolean): Boolean =
    validatorSet.contains(address) match {
      case true if authorize   => false
      case false if !authorize => false
      case _ =>
        if (tally.contains(address)) {
          val old = tally(address)
          tally += (address -> old.copy(votes = old.votes + 1))
        } else {
          tally += (address -> Tally(authorize, 1))
        }
        true
    }

  // uncast removes a previously cast vote from the tally.
  def uncast(address: Address, authorize: Boolean): Boolean =
    tally.get(address) match {
      case None                                => false // If there's no tally, it's a dangling vote, just drop
      case Some(t) if t.authorize != authorize => false // Ensure we only revert counted votes
      case Some(t) =>
        if (t.votes > 1) {
          tally += (address -> t.copy(votes = t.votes - 1))
        } else {
          tally -= address
        }
        true
    }

  // validators retrieves the list of authorized validators in ascending order.
  def getValidators: List[Address] = validatorSet.validators.toList.sorted

  def f: Int = Math.ceil(validatorSet.validators.size / 3.0).toInt - 1

}

object Snapshot {
  val namespace = ByteVector("istanbul".getBytes)

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

  implicit private[jbok] val byteArrayOrd: Ordering[Array[Byte]] = new Ordering[Array[Byte]] {
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

  implicit private[jbok] val addressOrd: Ordering[Address] = Ordering.by(_.bytes.toArray)

  def storeSnapshot[F[_]: Async](snapshot: Snapshot, db: KeyValueDB[F], checkpointInterval: Int)(
      implicit C: Cache[Snapshot]): F[Unit] =
    if (snapshot.number % checkpointInterval == 0) {
      db.put(snapshot.hash, snapshot.asJson.noSpaces, namespace) <* C.put[F](snapshot.hash)(snapshot)
    } else {
      C.put[F](snapshot.hash)(snapshot).void
    }

  def loadSnapshot[F[_]: Sync](db: KeyValueDB[F], hash: ByteVector)(implicit F: Async[F],
                                                                    C: Cache[Snapshot]): F[Option[Snapshot]] =
    C.get[F](hash).flatMap {
      case Some(snap) => Sync[F].pure(snap.some)
      case None =>
        db.getOpt[ByteVector, String](hash, namespace)
          .map(_.map(json => decode[Snapshot](json).right.get))
          .flatMap {
            case Some(snap) => C.put[F](hash)(snap).as(Some(snap))
            case None       => Sync[F].pure(None)
          }
    }

  def apply(config: IstanbulConfig, number: BigInt, hash: ByteVector, validatorSet: ValidatorSet): Snapshot =
    new Snapshot(config, number, hash, validatorSet, ArrayBuffer.empty, MMap.empty)

  // apply creates a new authorization snapshot by
  // applying the given headers to the original one.
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

      val snap = snapshot.copy()
      headers.foldLeftM(snap)((snap, header) => Snapshot.applyHeader(snap, header))
    }

  // create a new snapshot by applying a given header
  private def applyHeader[F[_]](snap: Snapshot, header: BlockHeader)(implicit F: Sync[F]): F[Snapshot] = F.delay {
    val number      = header.number
    val beneficiary = Address(header.beneficiary)

    // Clear any stale votes at each epoch
    if (snap.number % snap.config.epoch == 0) {
      snap.votes.clear()
      snap.tally.clear()
    }

    // Resolve the authorization key and check against signers
    val signer = Istanbul.ecrecover(header)
    if (!snap.validatorSet.contains(signer)) {
      throw new Exception("unauthorized signer")
    }

    // Tally up the new vote from the signer
    val authorize = ???
//      if (header.nonce == Istanbul.nonceAuthVote) {
//      true
//    } else if (header.nonce == Istanbul.nonceDropVote) {
//      false
//    } else {
//      throw new Exception("invalid vote")
//    }

    // Header authorized, discard any previous votes from the signer to prevent duplicated votes
    // Uncast the vote from the cached tally
    snap.votes
      .filter(x => x.signer == signer && x.address == beneficiary)
      .foreach(v => snap.uncast(v.address, v.authorize))

    // Uncast the vote from the chronological list
    val votes = snap.votes.filterNot(x => x.signer == signer && x.address == beneficiary)

    // Tally up the new vote from the signer
    if (snap.cast(beneficiary, authorize)) {
      votes += Vote(signer, number, beneficiary, authorize)
    }

    // If the vote passed, update the list of signers
    val (newVotes, newValidators) = snap.tally.get(beneficiary) match {
      case Some(t) if t.votes > snap.getValidators.size / 2 && t.authorize =>
        val finalValidators = snap.validatorSet.validators :+ beneficiary

        // Discard any previous votes around the just changed account
        val finalVotes = votes.filter(_.address != beneficiary)
        snap.tally -= beneficiary
        (finalVotes, finalValidators)

      case Some(t) if t.votes > snap.getValidators.size / 2 =>
        val finalValidators = snap.validatorSet.validators.filterNot(_ == beneficiary)

        // Discard any previous votes the deauthorized signer cast
        votes
          .filter(_.signer == beneficiary)
          .foreach(v => snap.uncast(v.address, v.authorize))

        val newVotes = votes.filter(_.signer != beneficiary)
        // Discard any previous votes around the just changed account
        val finalVotes = newVotes.filter(_.address != beneficiary)
        snap.tally -= beneficiary
        (finalVotes, finalValidators)

      case _ =>
        (votes, snap.validatorSet.validators)
    }

    snap.copy(
      number = snap.number + 1,
      hash = header.hash,
      votes = newVotes,
      validatorSet = snap.validatorSet.copy(validators = newValidators)
    )
  }
}
