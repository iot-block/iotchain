package jbok.core.nat

import jbok.JbokSpec

class NatSpec extends JbokSpec{

  "nat-pmp" should {
    "add mapping" in {
      val result = for {
        pmp <- Nat(NatPMP)
        result <- pmp.addMapping(12346,12346,120)
      }yield result

      result.attempt.unsafeRunSync() match {
        case Left(e) => {
          e.printStackTrace()
          assert(false)
        }
        case Right(s) => assert(s)
      }

      Thread.sleep(20*1000)
    }
    "delete mapping" in {
      val delResult = for {
        pmp <- Nat(NatPMP)
        result <- pmp.deleteMapping(12346,12346)
      }yield result

      delResult.attempt.unsafeRunSync() match {
        case Left(e) => {
          e.printStackTrace()
          assert(false)
        }
        case Right(s) => assert(s)
      }
    }
  }

  "upnp" should {
    "add mapping" in {
      val result = for {
        upnp <- Nat(NatUPnP)
        result <- upnp.addMapping(12346,12346,120)
      }yield result

      result.attempt.unsafeRunSync() match {
        case Left(e) => {
          e.printStackTrace()
          assert(false)
        }
        case Right(s) => assert(s)
      }

      Thread.sleep(20*1000)
    }

    "delete mapping" in {
      val delResult = for {
        upnp <- Nat(NatUPnP)
        result <- upnp.deleteMapping(12346,12346)
      }yield result

      delResult.attempt.unsafeRunSync() match {
        case Left(e) => {
          e.printStackTrace()
          assert(false)
        }
        case Right(s) => assert(s)
      }
    }
  }

}
