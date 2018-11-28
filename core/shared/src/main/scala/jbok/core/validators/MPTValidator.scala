package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.core.store.namespaces
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.KeyValueDB
import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

private[validators] object MPTValidator {

  /**
    * This function validates if a lists matches a Mpt Hash. To do so it inserts into an ephemeral MPT
    * (itemIndex, item) tuples and validates the resulting hash
    */
  def isValid[F[_]: Sync, V: Codec](hash: ByteVector, toValidate: List[V]): F[Boolean] =
    for {
      db   <- KeyValueDB.inmem[F]
      trie <- MerklePatriciaTrie[F](namespaces.empty, db)
      _    <- toValidate.zipWithIndex.map { case (v, k) => trie.put(k, v, namespaces.empty) }.sequence
      root <- trie.getRootHash
      r = root equals hash
    } yield r

}
