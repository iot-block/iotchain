package jbok.core.store

import cats.effect.IO
import jbok.JbokSpec
import jbok.core.models.{Account, Address}
import jbok.crypto.authds.mpt.MPTrie
import jbok.persistent.{KeyValueDB, RefCountKeyValueDB}

class StoreSpec extends JbokSpec {
  val db = KeyValueDB.inMemory[IO].unsafeRunSync()
  "AddressAccountStore" in {
    val rcdb    = RefCountKeyValueDB.forVersion(db, 1)
    val mpt     = MPTrie(rcdb).unsafeRunSync()
    val store   = AddressAccountStore(mpt)
    val addr    = Address(0x1)
    val account = Account(0, 100000)
    store.put(addr, account).unsafeRunSync()
    val rcdb2 = RefCountKeyValueDB.forVersion(db, 2)
    val mpt2 = MPTrie(rcdb2, Some(mpt.getRootHash.unsafeRunSync())).unsafeRunSync()
    val store2 = AddressAccountStore(mpt2)
    println(store2.get(addr).unsafeRunSync())

    store2.put(addr, account.copy(balance = 999)).unsafeRunSync()
    println(store2.get(addr).unsafeRunSync())
    println(store.get(addr).unsafeRunSync())
  }
}
