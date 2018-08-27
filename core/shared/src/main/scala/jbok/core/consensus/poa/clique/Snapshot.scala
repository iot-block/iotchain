package jbok.core.consensus.poa.clique

import cats.effect.Sync
import cats.implicits._
import io.circe.Decoder.Result
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import jbok.codec.json._
import jbok.core.consensus.poa.clique.Clique._
import jbok.core.consensus.poa.clique.Snapshot._
import jbok.core.models.{Address, BlockHeader}
import jbok.persistent.KeyValueStore
import scodec.bits._

import scala.collection.mutable.{ArrayBuffer, Map => MMap, Set => MSet}

// Vote represents a single vote that an authorized signer made to modify the
// list of authorizations.
case class Vote(
    signer: Address, // Authorized signer that cast this vote
    block: BigInt, // Block number the vote was cast in (expire old votes)
    address: Address, // Account being voted on to change its authorization
    authorize: Boolean // Whether to authorize or deauthorize the voted account
)

// Tally is a simple vote tally to keep the current score of votes. Votes that
// go against the proposal aren't counted since it's equivalent to not voting.
case class Tally(
    authorize: Boolean, // Whether the vote is about authorizing or kicking someone
    votes: Int // Number of votes until now wanting to pass the proposal
)

// Snapshot is the state of the authorization voting at a given point in time.
case class Snapshot(
    config: CliqueConfig,
    number: BigInt, // Block number where the snapshot was created
    hash: ByteVector, // Block hash where the snapshot was created
    signers: MSet[Address], // Set of authorized signers at this moment
    recents: MMap[BigInt, Address], // Set of recent signers for spam protections
    votes: ArrayBuffer[Vote], // List of votes cast in chronological order
    tally: MMap[Address, Tally] // Current vote tally to avoid recalculating
) {
  // cast adds a new vote into the tally.
  def cast(address: Address, authorize: Boolean): Boolean =
    signers.contains(address) match {
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

  // signers retrieves the list of authorized signers in ascending order.
  def getSigners: List[Address] = signers.toList.sorted

  // inturn returns if a signer at a given block height is in-turn or not.
  def inturn(number: BigInt, signer: Address): Boolean = {
    val signers = getSigners
    val offset = signers.zipWithIndex.collectFirst {
      case (address, index) if address == signer => index
    }
    offset.exists(i => number % BigInt(signers.length) == BigInt(i))
  }
}

object Snapshot {
  implicit private val addressKeyEncoder =
    KeyEncoder.instance[Address](_.bytes.asJson.noSpaces)
  implicit private val addressKeyDecoder =
    KeyDecoder.instance[Address](s => decode[ByteVector](s).map(bytes => Address(bytes)).right.toOption)
  implicit private val bigIntKeyEncoder =
    KeyEncoder.instance[BigInt](_.asJson.noSpaces)
  implicit private val bigIntKeyDecoder =
    KeyDecoder.instance[BigInt](s => decode[BigInt](s).right.toOption)
  implicit private val encoder: io.circe.Encoder[Snapshot] = new Encoder[Snapshot] {
    override def apply(a: Snapshot): Json = Json.obj(
      "config"  -> a.config.asJson,
      "number"  -> a.number.asJson,
      "hash"    -> a.hash.asJson,
      "signers" -> a.signers.toList.asJson,
      "recents" -> a.recents.toMap.asJson,
      "votes"   -> a.votes.toList.asJson,
      "tally"   -> a.tally.toMap.asJson
    )
  }
  implicit private val decoder: io.circe.Decoder[Snapshot] = new Decoder[Snapshot] {
    override def apply(c: HCursor): Result[Snapshot] =
      for {
        config  <- c.downField("config").as[CliqueConfig]
        number  <- c.downField("number").as[BigInt]
        hash    <- c.downField("hash").as[ByteVector]
        signers <- c.downField("signers").as[List[Address]]
        recents <- c.downField("recents").as[Map[BigInt, Address]]
        votes   <- c.downField("votes").as[List[Vote]]
        tally   <- c.downField("tally").as[Map[Address, Tally]]
      } yield {
        Snapshot(
          config,
          number,
          hash,
          MSet(signers: _*),
          MMap(recents.toSeq: _*),
          ArrayBuffer(votes: _*),
          MMap(tally.toSeq: _*)
        )
      }
  }

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

  def storeSnapshot[F[_]: Sync](snapshot: Snapshot, store: KeyValueStore[F, ByteVector, String]): F[Unit] =
    store.put(snapshot.hash, snapshot.asJson.noSpaces)

  def loadSnapshot[F[_]: Sync](store: KeyValueStore[F, ByteVector, String], hash: ByteVector): F[Option[Snapshot]] =
    store.getOpt(hash).map(_.map(json => io.circe.parser.decode[Snapshot](json).right.get))

  def apply(config: CliqueConfig, number: BigInt, hash: ByteVector, signers: Set[Address]): Snapshot =
    new Snapshot(config, number, hash, MSet(signers.toSeq: _*), MMap.empty, ArrayBuffer.empty, MMap.empty)

  // apply creates a new authorization snapshot by
  // applying the given headers to the original one.
  def applyHeaders(snapshot: Snapshot, headers: List[BlockHeader]): Snapshot =
    if (headers.isEmpty) {
      snapshot
    } else {
      // sanity check that the headers can be applied
      if (headers.sliding(2).exists {
            case left :: right :: Nil => left.number + 1 != right.number
            case _                    => false
          }) {
        throw new Exception("invalid voting chain")
      }

      if (headers.head.number != snapshot.number + 1) {
        throw new Exception("invalid voting chain")
      }

      val snap = snapshot.copy()
      headers.foldLeft(snap)((snap, header) => Snapshot.applyHeader(snap, header))
    }

  // create a new snapshot by applying a given header
  private def applyHeader[F[_]](snap: Snapshot, header: BlockHeader): Snapshot = {
    val number      = header.number
    val beneficiary = Address(header.beneficiary)

    // Clear any stale votes at each epoch
    if (snap.number % snap.config.epoch == 0) {
      snap.votes.clear()
      snap.tally.clear()
    }

    // Delete the oldest signer from the recent list to allow it signing again
    val limit = BigInt(snap.signers.size / 2 + 1)
    if (number >= limit) {
      snap.recents.remove(number - limit)
    }

    // Resolve the authorization key and check against signers
    val signer = Clique.ecrecover(header)
    if (!snap.signers.contains(signer)) {
      println(s"signers: ${snap.signers}")
      println(s"signer: ${signer}")
      throw new Exception("unauthorized signer")
    }
    if (snap.recents.exists(_._2 == signer)) {
      throw new Exception("signer has signed recently")
    }
    snap.recents += (number -> signer)

    // Tally up the new vote from the signer
    val authorize = if (header.nonce == nonceAuthVote) {
      true
    } else if (header.nonce == nonceDropVote) {
      false
    } else {
      throw new Exception("invalid vote")
    }

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
    val newVotes = snap.tally.get(beneficiary) match {
      case Some(t) if t.votes > snap.signers.size / 2 && t.authorize =>
        snap.signers += beneficiary

        // Discard any previous votes around the just changed account
        val finalVotes = votes.filter(_.address != beneficiary)
        snap.tally -= beneficiary
        finalVotes

      case Some(t) if t.votes > snap.signers.size / 2 =>
        snap.signers -= beneficiary

        // Signer list shrunk, delete any leftover recent caches
        val limit = BigInt(snap.signers.size / 2 + 1)
        if (number >= limit) {
          snap.recents -= (number - limit)
        }

        // Discard any previous votes the deauthorized signer cast
        votes.filter(_.signer == beneficiary)
          .foreach(v => snap.uncast(v.address, v.authorize))

        val newVotes = votes.filter(_.signer != beneficiary)
        // Discard any previous votes around the just changed account
        val finalVotes = newVotes.filter(_.address != beneficiary)
        snap.tally -= beneficiary
        finalVotes

      case _ =>
        votes
    }

    snap.copy(
      number = snap.number + 1,
      hash = header.hash,
      votes = newVotes
    )
  }
}
