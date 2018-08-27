package jbok.network.transport
import java.net.InetSocketAddress
import java.nio.channels.AsynchronousChannelGroup

import cats.effect.ConcurrentEffect
import cats.implicits._
import fs2.async.mutable.{Signal, Topic}
import fs2.async.{Promise, Ref}
import fs2.{Sink, _}
import jbok.network.Connection
import jbok.network.common.{RequestId, RequestMethod, TcpUtil}
import org.log4s.Logger
import scodec.Codec

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TcpTransport[F[_], A](
    val pipe: Pipe[F, A, A],
    val connections: Ref[F, Map[InetSocketAddress, Connection[F, A]]],
    val promises: Ref[F, Map[(InetSocketAddress, String), Promise[F, A]]],
    val topic: Topic[F, Option[TransportEvent[A]]],
    val stopListen: Signal[F, Boolean],
    val timeout: FiniteDuration
)(implicit F: ConcurrentEffect[F],
  AG: AsynchronousChannelGroup,
  EC: ExecutionContext,
  C: Codec[A],
  I: RequestId[A],
  M: RequestMethod[A],
  S: Scheduler)
    extends Transport[F, A] {
  private[this] val log: Logger = org.log4s.getLogger

  private def onDisconnect(remote: InetSocketAddress, incoming: Boolean): F[Unit] =
    for {
      _ <- connections.modify(_ - remote)
      _ <- topic.publish1(Some(TransportEvent.Drop(remote, incoming)))
      s = if (incoming) s"incoming from ${remote} disconnected" else s"outgoing to ${remote} disconnected"
      _ <- F.delay(log.info(s))
    } yield ()

  private def onError(e: Throwable): Stream[F, Unit] =
    Stream.eval(F.delay(log.warn(s"connection closed because ${e}")))

  override def connect(remote: InetSocketAddress, onConnect: Connection[F, A] => F[Unit] = _ => F.unit): F[Unit] =
    connections.get.flatMap(_.get(remote) match {
      case Some(_) =>
        log.info(s"already has a connection to ${remote}, ignored")
        F.unit
      case None =>
        F.start(
            fs2.io.tcp
              .client[F](remote, keepAlive = true, noDelay = true)
              .flatMap(socket => {
                val conn: Connection[F, A] = TcpUtil.socketToConnection[F, A](socket)
                for {
                  remote <- Stream.eval(conn.remoteAddress.map(_.asInstanceOf[InetSocketAddress]))
                  local  <- Stream.eval(conn.localAddress.map(_.asInstanceOf[InetSocketAddress]))
                  _ = log.info(s"connect from ${local} to ${remote}")
                  _ <- Stream.eval(connections.modify(_ + (remote -> conn)))
                  _ <- Stream.eval(onConnect(conn))
                  _ <- Stream.eval(topic.publish1(Some(TransportEvent.Add(remote, incoming = false))))
                  _ <- conn
                    .reads(Some(timeout))
                    .evalMap(a => {
                      val f = I.id(a) match {
                        case None =>
                          topic.publish1(Some(TransportEvent.Received(remote, a)))
                        case Some(id) =>
                          promises.get.flatMap(_.get((remote, id)) match {
                            case Some(promise) => promise.complete(a)
                            case None =>
                              topic.publish1(Some(TransportEvent.Received(remote, a)))
                          })
                      }

                      f.map(_ => a)
                    })
                    .through(pipe)
                    .to(conn.writes(Some(timeout)))
                    .onFinalize(onDisconnect(remote, false))
                    .handleErrorWith(onError)
                } yield ()
              })
              .compile
              .drain)
          .void
    })

  override def disconnect(remote: InetSocketAddress): F[Unit] =
    connections.get.flatMap(_.get(remote) match {
      case Some(conn) => conn.close
      case None       => F.unit
    })

  override def getConnected: F[Map[InetSocketAddress, Connection[F, A]]] =
    connections.get

  override def listen(bind: InetSocketAddress,
                      onConnect: Connection[F, A] => F[Unit] = _ => F.unit,
                      maxConcurrent: Int = Int.MaxValue,
                      receiveBufferSize: Int = 256 * 1024): F[Unit] =
    stopListen.get.flatMap {
      case false => F.unit
      case true =>
        def stream(done: Promise[F, Unit]): Stream[F, Unit] =
          fs2.io.tcp
            .serverWithLocalAddress[F](bind, maxQueued = 0, reuseAddress = true, receiveBufferSize = receiveBufferSize)
            .map {
              case Left(bindAddr) =>
                log.info(s"start listening to ${bindAddr}")
                Stream.eval(done.complete(()))
              case Right(s) =>
                s.flatMap(socket => {
                  val conn: Connection[F, A] = TcpUtil.socketToConnection[F, A](socket)
                  for {
                    remote <- Stream.eval(conn.remoteAddress.map(_.asInstanceOf[InetSocketAddress]))
                    _ <- Stream
                      .eval(connections.get)
                      .flatMap(_.get(remote) match {
                        case Some(_) =>
                          log.info(s"${remote} already connected, close")
                          Stream.eval(conn.close)
                        case None =>
                          log.info(s"accepted incoming ${remote}")
                          for {
                            _ <- Stream.eval(connections.modify(_ + (remote -> conn)))
                            _ <- Stream.eval(onConnect(conn))
                            _ <- Stream.eval(topic.publish1(Some(TransportEvent.Add(remote, incoming = true))))
                            _ <- conn
                              .reads(Some(timeout))
                              .evalMap(a => {
                                val f = I.id(a) match {
                                  case None =>
                                    topic.publish1(TransportEvent.Received(remote, a).some)
                                  case Some(id) =>
                                    promises.get.flatMap(_.get((remote, id)) match {
                                      case Some(promise) => promise.complete(a)
                                      case None =>
                                        topic.publish1(TransportEvent.Received(remote, a).some)
                                    })
                                }

                                f.map(_ => a)
                              })
                              .through(pipe)
                              .to(conn.writes(Some(timeout)))
                              .onFinalize(onDisconnect(remote, true))
                              .handleErrorWith(onError)
                          } yield ()
                      })
                  } yield ()
                })
            }
            .join(maxConcurrent)
            .handleErrorWith(e => Stream.eval[F, Unit](F.delay(log.error(s"server error: ${e}"))))
            .onFinalize(stopListen.set(true) *> F.delay(log.info(s"stop listening to ${bind}")))

        for {
          _    <- stopListen.set(false)
          done <- fs2.async.promise[F, Unit]
          _    <- F.start(stream(done).interruptWhen(stopListen).compile.drain).void
          _    <- done.get
        } yield ()
    }

  override def stop: F[Unit] =
    stopListen.set(true) *> getConnected.flatMap(_.values.toList.traverse(_.close).void)

  override def broadcast(a: A): F[Unit] =
    for {
      conns <- connections.get.map(_.values.toList)
      _ <- conns match {
        case Nil =>
          log.info(s"empty connections, ignore")
          F.unit
        case _ =>
          log.info(s"broadcast a message to ${conns.length} connections")
          conns.traverse(_.write(a, Some(timeout)))
      }
    } yield ()

  override def write(remote: InetSocketAddress, a: A): F[Unit] =
    connections.get.flatMap(_.get(remote) match {
      case Some(conn) => conn.write(a, Some(timeout))
      case None       => F.unit
    })

  override def writes: Sink[F, (InetSocketAddress, A)] =
    _.evalMap { case (remote, a) => write(remote, a) }

  override def request(remote: InetSocketAddress, a: A): F[Option[A]] =
    connections.get.flatMap(_.get(remote) match {
      case None => F.pure(None)
      case Some(conn) =>
        for {
          promise <- fs2.async.promise[F, A]
          id = I.id(a).getOrElse("")
          _    <- promises.modify(_ + ((remote, id) -> promise))
          _    <- conn.write(a, Some(timeout))
          resp <- promise.timedGet(timeout, S)
          _    <- promises.modify(_ - (remote -> id))
        } yield resp
    })

  override def read(remote: InetSocketAddress): F[Option[A]] =
    connections.get.flatMap(_.get(remote) match {
      case Some(conn) => conn.read(Some(timeout))
      case None       => F.pure(None)
    })

  override def reads(remote: InetSocketAddress): Stream[F, A] =
    Stream
      .eval(connections.get)
      .flatMap(_.get(remote) match {
        case Some(conn) => conn.reads(Some(timeout))
        case None       => Stream.empty.covary[F]
      })

  override def subscribe(maxQueued: Int): Stream[F, TransportEvent[A]] =
    topic.subscribe(maxQueued).unNone
}

object TcpTransport {
  def apply[F[_]: ConcurrentEffect, A: Codec: RequestId: RequestMethod](pipe: Pipe[F, A, A],
                                                                        timeout: FiniteDuration = 10.seconds)(
      implicit AG: AsynchronousChannelGroup,
      EC: ExecutionContext,
      S: Scheduler): F[TcpTransport[F, A]] =
    for {
      connections <- fs2.async.refOf[F, Map[InetSocketAddress, Connection[F, A]]](Map.empty)
      promises    <- fs2.async.refOf[F, Map[(InetSocketAddress, String), Promise[F, A]]](Map.empty)
      topic       <- fs2.async.topic[F, Option[TransportEvent[A]]](None)
      stopListen  <- fs2.async.signalOf[F, Boolean](true)
    } yield
      new TcpTransport[F, A](
        pipe,
        connections,
        promises,
        topic,
        stopListen,
        timeout
      )
}
