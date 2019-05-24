package jbok.app

import java.nio.channels.FileLock
import java.nio.file.Paths

import cats.effect._
import fs2._
import jbok.app.service.{HttpService, StoreUpdateService}
import jbok.common.FileUtil
import jbok.common.log.Logger
import jbok.core.CoreNode

final class FullNode[F[_]](
    core: CoreNode[F],
    httpService: HttpService[F],
    storeUpdateService: StoreUpdateService[F]
)(implicit F: ConcurrentEffect[F]) {
  private[this] val log = Logger[F]

  def lock: Resource[F, FileLock] =
    FileUtil[F].lock(Paths.get(s"${core.config.rootPath}").resolve("LOCK"))

  def stream: Stream[F, Unit] =
    Stream.resource(lock).flatMap { _ =>
      Stream.eval_(log.i(s"staring FullNode...")) ++
        Stream(
          core.stream,
          httpService.stream,
          storeUpdateService.stream
        ).parJoinUnbounded
          .handleErrorWith(e => Stream.eval(log.e("FullNode has an unhandled failure", e)))
          .onFinalize(log.i(s"FullNode ready to exit, bye bye..."))
    }
}
