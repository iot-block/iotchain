package jbok.persistent
import cats.effect.IO
import jbok.common.testkit._
import org.scalacheck.Arbitrary

object testkit extends testkit
trait testkit {
  implicit def arbDB: Arbitrary[KeyValueDB[IO]] = Arbitrary {
    for {
      namespace <- arbByteVector.arbitrary
      db = KeyValueDB.inmem[IO].unsafeRunSync()
    } yield db
  }
}
