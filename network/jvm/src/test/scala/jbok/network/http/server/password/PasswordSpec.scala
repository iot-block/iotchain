package jbok.network.http.server.password

import jbok.common.CommonSpec

class PasswordSpec extends CommonSpec {
  "Password Hash" should {
    "hash pw use SCrypt" in {
      val hash     = Password.hash("oho".toCharArray).unsafeRunSync()
      val verified = Password.verify("oho".toCharArray, hash).unsafeRunSync()
      verified shouldBe true
    }
  }
}
