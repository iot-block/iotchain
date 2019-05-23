package jbok.network.http.client

import cats.effect._
import javax.net.ssl.SSLContext
import jbok.common.thread.ThreadUtil
import jbok.network.http.client.middleware.{LoggerMiddleware, MetricsMiddleware, RetryMiddleware}
import okhttp3.OkHttpClient
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.okhttp.OkHttpBuilder

object HttpClients {

  def withMiddlewares[F[_]](client: Client[F])(implicit F: Concurrent[F], T: Timer[F]): F[Client[F]] =
    MetricsMiddleware(LoggerMiddleware()(RetryMiddleware()(client)))

  def okHttp[F[_]](sslContext: Option[SSLContext] = None)(implicit F: ConcurrentEffect[F], cs: ContextShift[F]): Resource[F, Client[F]] =
    ThreadUtil.blockingThreadPool[F]("jbok-okhttp-client").flatMap { blockEC =>
      val builder = sslContext match {
        case Some(ctx) => new OkHttpClient.Builder().sslSocketFactory(ctx.getSocketFactory)
        case None      => new OkHttpClient.Builder()
      }

      OkHttpBuilder[F](builder.build(), blockEC).resource
    }

  def blaze[F[_]](sslContext: Option[SSLContext] = None)(implicit F: ConcurrentEffect[F]): Resource[F, Client[F]] =
    ThreadUtil.blockingThreadPool[F]("jbok-blaze-client").flatMap { blockEC =>
      BlazeClientBuilder[F](blockEC)
        .withSslContextOption(sslContext)
        .resource
    }
}
