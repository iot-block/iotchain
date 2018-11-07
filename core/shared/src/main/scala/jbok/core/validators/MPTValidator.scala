package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.core.store.namespaces
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.KeyValueDB
import scodec.Codec
import scodec.bits.ByteVector
import jbok.codec.rlp.implicits._

class MPTValidator[F[_]](implicit F: Sync[F]) {

  /**
    * This function validates if a lists matches a Mpt Hash. To do so it inserts into an ephemeral MPT
    * (itemIndex, item) tuples and validates the resulting hash
    *
    * @param hash       Hash to expect
    * @param toValidate Items to validate and should match the hash
    * @tparam V Type of the items cointained within the List
    * @return true if hash matches trie hash, false otherwise
    */
  def isValid[V: Codec](hash: ByteVector, toValidate: List[V]): F[Boolean] =
    for {
      db   <- KeyValueDB.inmem[F]
      trie <- MerklePatriciaTrie[F](namespaces.empty, db)
      _    <- toValidate.zipWithIndex.map { case (v, k) => trie.put(k, v, namespaces.empty) }.sequence
      root <- trie.getRootHash
      r = root equals hash
    } yield r

}
