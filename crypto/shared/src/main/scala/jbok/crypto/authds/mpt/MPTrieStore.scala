package jbok.crypto.authds.mpt

import cats.effect.Sync
import cats.effect.concurrent.Ref
import cats.implicits._
import jbok.codec.rlp.RlpCodec
import jbok.persistent.{KeyValueDB, KeyValueStore}
import scodec.bits.ByteVector

class MPTrieStore[F[_], K, V](namespace: ByteVector, mpt: MPTrie[F])(implicit F: Sync[F],
                                                                     ck: RlpCodec[K],
                                                                     cv: RlpCodec[V])
    extends KeyValueStore[F, K, V](namespace, mpt) {

  def getRootHash: F[ByteVector] = mpt.getRootHash

  def getNodeByHash(hash: ByteVector): F[Option[Node]] = mpt.getNodeByHash(hash)

  def size: F[Int] = mpt.size

  def clear(): F[Unit] = mpt.clear()
}

object MPTrieStore {
  def apply[F[_], K, V](db: KeyValueDB[F], rootHash: Option[ByteVector] = None)(implicit F: Sync[F], ck: RlpCodec[K], cv: RlpCodec[V]): F[MPTrieStore[F, K, V]] =
    for {
      rootHashRef <- Ref.of[F, Option[ByteVector]](rootHash)
      trie = new MPTrie[F](db, rootHashRef)
    } yield new MPTrieStore[F, K, V](ByteVector.empty, trie)
}
