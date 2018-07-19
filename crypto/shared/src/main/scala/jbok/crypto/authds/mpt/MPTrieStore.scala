package jbok.crypto.authds.mpt

import cats.effect.Sync
import cats.implicits._
import jbok.persistent.{KeyValueDB, KeyValueStore}
import scodec.Codec
import scodec.bits.ByteVector

class MPTrieStore[F[_], K, V](namespace: ByteVector, mpt: MPTrie[F])(implicit F: Sync[F], ck: Codec[K], cv: Codec[V])
    extends KeyValueStore[F, K, V](namespace, mpt) {

  def getRootHash: F[ByteVector] = mpt.getRootHash

  def getRoot: F[Node] = mpt.getRoot

  def getNodeByHash(hash: ByteVector): F[Node] = mpt.getNodeByHash(hash)

  def size: F[Int] = mpt.size

  def clear(): F[Unit] = mpt.clear()
}

object MPTrieStore {
  def apply[F[_], K, V](db: KeyValueDB[F])(implicit F: Sync[F], ck: Codec[K], cv: Codec[V]): F[MPTrieStore[F, K, V]] =
    for {
      rootHash <- fs2.async.refOf[F, ByteVector](MPTrie.emptyRootHash)
      trie = new MPTrie[F](db, rootHash)
    } yield new MPTrieStore[F, K, V](ByteVector.empty, trie)

  def inMemory[F[_], K, V](implicit F: Sync[F], ck: Codec[K], cv: Codec[V]): F[MPTrieStore[F, K, V]] =
    for {
      db <- KeyValueDB.inMemory[F]
      rootHash <- fs2.async.refOf[F, ByteVector](MPTrie.emptyRootHash)
      trie = new MPTrie[F](db, rootHash)
    } yield new MPTrieStore[F, K, V](ByteVector.empty, trie)
}
