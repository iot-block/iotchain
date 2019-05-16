package jbok.network.http

import cats.effect.{ConcurrentEffect, ContextShift, Resource}
import jbok.common.thread.ThreadUtil
import org.http4s.client.Client
import org.http4s.client.asynchttpclient.AsyncHttpClient
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.client.okhttp.OkHttpBuilder

object HttpClients {
  def okHttp[F[_]](implicit F: ConcurrentEffect[F], cs: ContextShift[F]): Resource[F, Client[F]] =
    ThreadUtil.blockingThreadPool[F]("jbok-okhttp-client").flatMap { blockEC =>
      OkHttpBuilder
        .withDefaultClient[F](blockEC)
        .map(_.create)
    }

  def ahc[F[_]](implicit F: ConcurrentEffect[F], cs: ContextShift[F]): Resource[F, Client[F]] =
    AsyncHttpClient.resource[F]()

  def blaze[F[_]](implicit F: ConcurrentEffect[F], cs: ContextShift[F]): Resource[F, Client[F]] =
    ThreadUtil.blockingThreadPool[F]("jbok-blaze-client").flatMap { blockEC =>
      BlazeClientBuilder[F](blockEC).resource
    }
}
