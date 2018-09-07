package jbok.core.validators

import cats.effect.Sync
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.codec.rlp.codecs._
import jbok.crypto.authds.mpt.MPTrieStore
import jbok.persistent.KeyValueDB
import scodec.bits.ByteVector

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
  def isValid[V](hash: ByteVector, toValidate: List[V])(implicit cv: RlpCodec[V]): F[Boolean] =
    for {
      db <- KeyValueDB.inMemory[F]
      trie <- MPTrieStore[F, Int, V](db)
      _ <- toValidate.zipWithIndex.map { case (v, k) => trie.put(k, v) }.sequence
      root <- trie.getRootHash
      r = root equals hash
    } yield r

}
