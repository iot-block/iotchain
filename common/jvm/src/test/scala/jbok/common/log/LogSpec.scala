package jbok.common.log

import cats.effect.IO
import jbok.JbokSpec

class LogSpec extends JbokSpec {
  "Scribe Log" should {
    "set level" in {
      val log = getLogger("oho")
      log.info(s"should be seen")
      ScribeLog.setRootLevel[IO](Level.Error).unsafeRunSync()
      log.info(s"should not be seen")
    }
  }
}
