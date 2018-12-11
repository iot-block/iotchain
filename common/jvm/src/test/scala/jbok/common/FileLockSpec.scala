package jbok.common
import java.nio.channels.OverlappingFileLockException

import better.files.File
import cats.effect.IO
import jbok.JbokSpec

class FileLockSpec extends JbokSpec {
  "FileLock" should {
    "release lock whatever use" in {
      val file = File.newTemporaryFile()
      val p = FileLock
        .lock[IO](file.path)
        .use { _ =>
          IO.raiseError(new Exception("boom"))
        }
        .attempt
      p.unsafeRunSync()
      file.exists shouldBe false
    }

    "raise OverlappingFileLockException if already locked" in {
      val file = File.newTemporaryFile()
      val p = FileLock
        .lock[IO](file.path)
        .use { _ =>
          FileLock
            .lock[IO](file.path)
            .use { _ =>
              IO.unit
            }
            .attempt
            .map(x => x.left.get shouldBe a[OverlappingFileLockException])
        }
        .attempt
      p.unsafeRunSync().isRight shouldBe true
      file.exists shouldBe false
    }
  }
}
