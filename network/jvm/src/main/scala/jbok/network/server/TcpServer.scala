package jbok.network.server

import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect._
import fs2._
import jbok.network.Message
import jbok.network.common.TcpUtil

object TcpServer {
  private[this] val log = jbok.common.log.getLogger("TcpServer")

  def bind[F[_]](bind: InetSocketAddress, pipe: Pipe[F, Message[F], Message[F]], maxOpen: Int = Int.MaxValue)(
      implicit F: ConcurrentEffect[F],
      CS: ContextShift[F],
      T: Timer[F],
      AG: AsynchronousChannelGroup): Server[F] = {

    val stream = fs2.io.tcp.Socket
      .serverWithLocalAddress[F](bind)
      .map {
        case Left(_) =>
          Stream.eval(F.delay(log.info(s"tcp server successfully bound to ${bind}")))
        case Right(res) =>
          for {
            conn <- Stream.eval(TcpUtil.socketToConnection[F](res, true))
            _    <- Stream.eval(conn.start)
            _    <- conn.reads.through(pipe).to(conn.sink)
          } yield ()
      }
      .parJoin(maxOpen)

    Server(bind, stream)
  }
}
