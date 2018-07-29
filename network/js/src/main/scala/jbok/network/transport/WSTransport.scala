//package jbok.network.transport
//
//import cats.effect.{Effect, IO}
//import cats.implicits._
//import io.circe.parser._
//import fs2.async.mutable.{Signal, Topic}
//import fs2.async.{Promise, Ref}
//import io.circe.Json
//import jbok.network.NetAddress
//import org.scalajs.dom
//
//import scala.concurrent.ExecutionContext
//
//abstract class WSTransport[F[_]](addr: NetAddress)(implicit F: Effect[F], ec: ExecutionContext)
//    extends DuplexTransport[F, String, String](addr)
//
//object WSTransport {
//  def apply[F[_]](addr: NetAddress)(implicit F: Effect[F], ec: ExecutionContext): F[WSTransport[F]] = {
//
//    for {
//      _topics <- fs2.async.refOf[F, Map[String, Topic[F, Option[String]]]](Map.empty)
//      _promises <- fs2.async.Ref[F, Map[String, Promise[F, String]]](Map.empty)
//      _stopWhenTrue <- fs2.async.signalOf[F, Boolean](true)
//    } yield
//      new WSTransport[F](addr) {
//        val url = s"ws://${addr.host}:${addr.port.getOrElse("")}"
//
//        val socket = new dom.WebSocket(url)
//
//        socket.onopen = { e: dom.Event =>
//          F.runAsync(stopWhenTrue.set(false))(_ => IO.unit).unsafeRunSync()
//        }
//
//        socket.onclose = { e: dom.CloseEvent =>
//          F.runAsync(stopWhenTrue.set(true))(_ => IO.unit).unsafeRunSync()
//        }
//
//        socket.onerror = { e: dom.ErrorEvent =>
//          F.runAsync(stopWhenTrue.set(true))(_ => IO.unit).unsafeRunSync()
//        }
//
//        socket.onmessage = { e: dom.MessageEvent =>
//          F.runAsync(handle(e.data.toString))(_ => IO.unit).unsafeRunSync()
//        }
//
//        override val stopWhenTrue: Signal[F, Boolean] = _stopWhenTrue
//
//        override val topics: Ref[F, Map[String, Topic[F, Option[String]]]] = _topics
//
//        override val promises: Ref[F, Map[String, Promise[F, String]]] = _promises
//
//        override def parseId(x: String): F[(Option[String], String)] =
//          (parse(x).getOrElse(Json.Null).hcursor.get[String]("id") match {
//            case Left(e) => (None, x)
//            case Right(id) => (Some(id), x)
//          }).pure[F]
//
//
//        override def parseMethod(x: String): F[(String, String)] =
//          F.delay(parse(x).getOrElse(Json.Null).hcursor.get[String]("method").right.get -> x)
//
//
//        override def send(req: String): F[Unit] = F.delay(socket.send(req))
//
//        override def start: F[Unit] = stopWhenTrue.discrete.takeWhile(_ == true).compile.drain
//
//        override def stop: F[Unit] = stopWhenTrue.set(true) *> F.delay(socket.close(1000, "shutdown"))
//      }
//  }
//}
