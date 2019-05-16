package jbok.network.http

import cats.effect.Async
import cats.implicits._
import jbok.network.rpc._

object HttpTransport {

  def apply[F[_]](baseUri: String)(implicit F: Async[F]): RpcTransport[F, String] =
    new RpcTransport[F, String] {
      override def fetch(request: RpcRequest[String]): F[String] = {
        val uri = (baseUri :: request.path).mkString("/")
        for {
          response <- HttpClient.post[F](uri, request.payload)
        } yield response.data
      }
    }

//      private val sender =
//        sendRequest[F, P, Exception](baseUri, (r, c) => new Exception(s"Http request failed $r: $c")) _
//
//      def fetch(request: RpcRequest[P]): F[P] =
//        sender(request).flatMap {
//          case Right(res) => F.pure(res)
//          case Left(err)  => F.raiseError(err)
//        }
}

//  private def sendRequest[F[_]](baseUri: String)(request: RpcRequest[String])(implicit F: Async[F]): F[String] = {

//    val http = new dom.XMLHttpRequest
//    http.responseType = builder.responseType
////    def failedRequest = failedRequestError(uri, http.status)
//
//    http.open("POST", uri, true)
//    http.onreadystatechange = { _: dom.Event =>
//      if (http.readyState == 4)
//        if (http.status == 200) {
//          val value = (http.response: Any) match {
//            case s: String      => builder.unpack(s).map(_.toRight(failedRequest))
//            case a: ArrayBuffer => builder.unpack(a).map(_.toRight(failedRequest))
//            case b: dom.Blob    => builder.unpack(b).map(_.toRight(failedRequest))
//            case _              => F.pure(Left(failedRequest))
//          }
//          promise.completeWith(value.unsafeToFuture())
//        } else promise.trySuccess(Left(failedRequest))
//    }
//
//    val message = builder.pack(request.payload)
//    (message: Any) match {
//      case s: String      => Try(http.send(s))
//      case a: ArrayBuffer => Try(http.send(a))
//      case b: dom.Blob    => Try(http.send(b))
//    }
//
//    F.liftIO(IO.fromFuture(IO(promise.future)))
//  }

//}
//
//class PlatformHttpClient[F[_]](implicit F: Async[F], ec: ExecutionContext) {
//  implicit val clock: Clock[F] = Clock.create[F]
//
//  def apply[Payload](
//      baseUri: String,
//  )(implicit ec: ExecutionContext, builder: JsMessageBuilder[IO, Payload]): RpcClient[F, Payload] = {
//
//    val transport: RpcTransport[F, Payload] = new RpcTransport[F, Payload] {
//      private val sender =
//        sendRequest[Payload, Exception](baseUri, (r, c) => new Exception(s"Http request failed $r: $c")) _
//
//      def fetch(request: RpcRequest[Payload]): F[Payload] =
//        sender(request).flatMap {
//          case Right(res) => F.pure(res)
//          case Left(err)  => F.raiseError(err)
//        }
//    }
//
//    RpcClient[F, Payload](transport)
//  }
//
//  private def sendRequest[Payload, Error](
//      baseUri: String,
//      failedRequestError: (String, Int) => Error
//  )(request: RpcRequest[Payload])(implicit ec: ExecutionContext, builder: JsMessageBuilder[IO, Payload]): F[Either[Error, Payload]] = {
//
//    val uri     = (baseUri :: request.path).mkString("/")
//    val promise = Promise[Either[Error, Payload]]
//
//    val http = new dom.XMLHttpRequest
//    http.responseType = builder.responseType
//    def failedRequest = failedRequestError(uri, http.status)
//
//    http.open("POST", uri, true)
//    http.onreadystatechange = { _: dom.Event =>
//      if (http.readyState == 4)
//        if (http.status == 200) {
//          val value = (http.response: Any) match {
//            case s: String      => builder.unpack(s).map(_.toRight(failedRequest))
//            case a: ArrayBuffer => builder.unpack(a).map(_.toRight(failedRequest))
//            case b: dom.Blob    => builder.unpack(b).map(_.toRight(failedRequest))
//            case _              => IO.pure(Left(failedRequest))
//          }
//          promise.completeWith(value.unsafeToFuture())
//        } else promise.trySuccess(Left(failedRequest))
//    }
//
//    val message = builder.pack(request.payload)
//    (message: Any) match {
//      case s: String      => Try(http.send(s))
//      case a: ArrayBuffer => Try(http.send(a))
//      case b: dom.Blob    => Try(http.send(b))
//    }
//
//    F.liftIO(IO.fromFuture(IO(promise.future)))
//  }
//}
