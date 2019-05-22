package jbok.crypto.ssl

import java.nio.file.Paths
import java.security.KeyStore

import cats.effect.Sync
import cats.implicits._
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLEngine, TrustManagerFactory}
import jbok.common.FileUtil
import jbok.common.log.Logger

final class ClientSSLEngine(val engine: SSLEngine) extends AnyVal

final class ServerSSLEngine(val engine: SSLEngine) extends AnyVal

object SSLContextHelper {
  def apply[F[_]](config: SSLConfig)(implicit F: Sync[F]): F[Option[SSLContext]] =
    if (!config.enabled) {
      F.pure(None)
    } else {
      Logger[F].i(s"init SSLContext from keyStore=${config.keyStorePath} trustStore=${config.trustStorePath}") >>
        FileUtil[F]
          .inputStream(Paths.get(config.keyStorePath))
          .use { keyStoreIS =>
            FileUtil[F].inputStream(Paths.get(config.trustStorePath)).use { trustStoreIS =>
              F.delay {
                val keyStore = KeyStore.getInstance("JKS")
                keyStore.load(keyStoreIS, "changeit".toCharArray)
                val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
                keyManagerFactory.init(keyStore, "changeit".toCharArray)

                val trustStore = KeyStore.getInstance("JKS")
                trustStore.load(trustStoreIS, "changeit".toCharArray)
                val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
                trustManagerFactory.init(trustStore)

                val ctx = SSLContext.getInstance(config.protocol)
                ctx.init(keyManagerFactory.getKeyManagers, trustManagerFactory.getTrustManagers, null)
                ctx
              }
            }
          }
          .map(_.some)
    }

  def clientEngine(ctx: SSLContext): ClientSSLEngine = {
    val engine = ctx.createSSLEngine()
    engine.setUseClientMode(true)
    engine.setNeedClientAuth(false)
    new ClientSSLEngine(engine)
  }

  def serverEngine(ctx: SSLContext): ServerSSLEngine = {
    val engine = ctx.createSSLEngine()
    engine.setUseClientMode(false)
    engine.setNeedClientAuth(false)
    new ServerSSLEngine(engine)
  }
}
