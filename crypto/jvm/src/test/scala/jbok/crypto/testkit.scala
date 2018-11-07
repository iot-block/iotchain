package jbok.crypto
import cats.effect.IO
import jbok.common.testkit._
import jbok.crypto.authds.mpt.MerklePatriciaTrie
import jbok.persistent.KeyValueDB
import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary._
import scodec.bits.ByteVector

object testkit extends testkit
trait testkit {
  implicit def arbMerkleTrie: Arbitrary[MerklePatriciaTrie[IO]] = Arbitrary {
    for {
      namespace <- arbitrary[ByteVector]
      db = KeyValueDB.inmem[IO].unsafeRunSync()
    } yield MerklePatriciaTrie[IO](namespace, db).unsafeRunSync()
  }
}
