package jbok.core.config

import cats.effect.Sync
import cats.implicits._
import io.circe.generic.JsonCodec
import jbok.core.keystore.KeyStore
import jbok.core.models.Address
import jbok.crypto.signature.KeyPair
import jbok.codec.json.implicits._

import scala.concurrent.duration.FiniteDuration

@JsonCodec
final case class MiningConfig(
    enabled: Boolean,
    address: Address,
    passphrase: String,
    coinbase: Address,
    period: FiniteDuration,
    epoch: Int,
    checkpointInterval: Int,
    minBroadcastPeers: Int
)

object MiningConfig {
  def getKeyPair[F[_]](config: MiningConfig, keyStore: KeyStore[F])(implicit F: Sync[F]): F[Option[KeyPair]] =
    if (config.enabled) {
      keyStore.unlockAccount(config.coinbase, config.passphrase).map(_.keyPair.some)
    } else F.pure(None)
}
