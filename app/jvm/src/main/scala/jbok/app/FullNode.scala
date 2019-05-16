package jbok.app

import cats.effect._
import fs2._
import jbok.app.service.HttpService
import jbok.common.log.Logger
import jbok.core.CoreNode

final class FullNode[F[_]](
    core: CoreNode[F],
    httpService: HttpService[F]
)(implicit F: ConcurrentEffect[F], T: Timer[F]) {
  private[this] val log = Logger[F]

//  def lock: Resource[F, FileLock] =
//    FileUtil[F].lock(Paths.get(s"${core.config.lockPath}"))

  def stream: Stream[F, Unit] =
//    Stream.resource(lock).flatMap { _ =>
    Stream.eval_(log.i(s"staring FullNode...")) ++
      Stream(
        core.stream,
        httpService.stream
      ).parJoinUnbounded
        .handleErrorWith(e => Stream.eval(log.e("FullNode has an unhandled failure", e)))
        .onFinalize(log.i(s"FullNode ready to exit, bye bye..."))
}
