package jbok.persistent

import cats.effect.IO
import org.scalacheck.Arbitrary

object testkit {
  implicit def arbDB: Arbitrary[KeyValueDB[IO]] = Arbitrary {
    KeyValueDB.inmem[IO].unsafeRunSync()
  }
}
