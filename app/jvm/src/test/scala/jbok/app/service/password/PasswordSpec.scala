package jbok.app.service.password
import jbok.JbokSpec

class PasswordSpec extends JbokSpec {
  "Password Hash" should {
    "hash pw use SCrypt" in {
      val hash     = Password.hash("oho".toCharArray).unsafeRunSync()
      val verified = Password.verify("oho".toCharArray, hash).unsafeRunSync()
      verified shouldBe true
    }
  }
}
