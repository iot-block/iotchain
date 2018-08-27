package jbok.persistent

import scala.collection.SortedMap

/**
  * Immutable implementation of an LRU cache.
  */
object ImmutableLruMap {

  /**
    * Build an immutable LRU key/value store that cannot grow larger than `maxSize`
    */
  def apply[K, V](maxSize: Int): ImmutableLruMap[K, V] =
    new ImmutableLruMap(maxSize, 0, Map.empty[K, (Long, V)], SortedMap.empty[Long, K])
}

/**
  * An immutable key/value store that evicts the least recently accessed elements
  * to stay constrained in a maximum size bound.
  */
// "map" is the backing store used to hold key->(index,value)
// pairs. The index tracks the access time for a particular key. "ord"
// is used to determine the Least-Recently-Used key in "map" by taking
// the minimum index.
class ImmutableLruMap[K, V] private (
    maxSize: Int,
    idx: Long,
    map: Map[K, (Long, V)],
    ord: SortedMap[Long, K]
) {

  // Scala's SortedMap requires a key ordering; ImmutableLRU doesn't
  // care about pulling a minimum value out of the SortedMap, so the
  // following kOrd treats every value as equal.
  implicit protected val kOrd = new Ordering[K] { def compare(l: K, r: K) = 0 }

  /**
    * the number of entries in the cache
    */
  def size: Int = map.size

  /**
    * the `Set` of all keys in the LRU
    * @note accessing this set does not update the element LRU ordering
    */
  def keySet: Set[K] = map.keySet

  /**
    * Build a new LRU containing the given key/value
    * @return a tuple with of the following two items:
    *          _1 represents the evicted entry (if the given lru is at the maximum size)
    *             or None if the lru is not at capacity yet.
    *          _2 is the new lru with the given key/value pair inserted.
    */
  def +(kv: (K, V)): (Option[K], ImmutableLruMap[K, V]) = {
    val (key, value) = kv
    val newIdx       = idx + 1
    val newMap       = map + (key -> ((newIdx, value)))
    // Now update the ordered cache:
    val baseOrd       = map.get(key).map { case (id, _) => ord - id }.getOrElse(ord)
    val ordWithNewKey = baseOrd + (newIdx -> key)
    // Do we need to remove an old key:
    val (evicts, finalMap, finalOrd) = if (ordWithNewKey.size > maxSize) {
      val (minIdx, eKey) = ordWithNewKey.min
      (Some(eKey), newMap - eKey, ordWithNewKey - minIdx)
    } else {
      (None, newMap, ordWithNewKey)
    }
    (evicts, new ImmutableLruMap[K, V](maxSize, newIdx, finalMap, finalOrd))
  }

  /**
    * If the key is present in the cache, returns the pair of
    * Some(value) and the cache with the key's entry as the most recently accessed.
    * Else, returns None and the unmodified cache.
    */
  def get(k: K): (Option[V], ImmutableLruMap[K, V]) = {
    val (optionalValue, lru) = remove(k)
    val newLru = optionalValue.map { v =>
      (lru + (k -> v))._2
    } getOrElse lru
    (optionalValue, newLru)
  }

  /**
    * If the key is present in the cache, returns the pair of
    * Some(value) and the cache with the key removed.
    * Else, returns None and the unmodified cache.
    */
  def remove(k: K): (Option[V], ImmutableLruMap[K, V]) =
    map
      .get(k)
      .map {
        case (kidx, v) =>
          val newMap = map - k
          val newOrd = ord - kidx
          // Note we don't increase the idx on a remove, only on put:
          (Some(v), new ImmutableLruMap[K, V](maxSize, idx, newMap, newOrd))
      }
      .getOrElse(None, this)

  override def toString = "ImmutableLruMap(" + map.toList.mkString(",") + ")"
}
