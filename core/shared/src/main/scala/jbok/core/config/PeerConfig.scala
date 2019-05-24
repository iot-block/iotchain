package jbok.core.config
import java.net.InetSocketAddress

import io.circe.generic.JsonCodec
import jbok.core.peer.PeerUri

import scala.concurrent.duration.FiniteDuration
import jbok.codec.json.implicits._

@JsonCodec
final case class PeerConfig(
    host: String,
    port: Int,
    seeds: List[String],
    updatePeersInterval: FiniteDuration,
    maxOutgoingPeers: Int,
    maxIncomingPeers: Int,
    minPeers: Int,
    bufferSize: Int,
    timeout: FiniteDuration
) {
  val bindAddr: InetSocketAddress = new InetSocketAddress(host, port)
  val seedUris: List[PeerUri]     = seeds.flatMap(s => PeerUri.fromStr(s).toOption.toList)
}

object PeerConfig {
//  def loadNodeKey(path: String): KeyPair = {
//    val line   = File(path).lines(DefaultCharset).headOption.getOrElse("")
//    val secret = KeyPair.Secret(line)
//    val pubkey = Signature[ECDSA].generatePublicKey[IO](secret).unsafeRunSync()
//    KeyPair(pubkey, secret)
//  }
//
//  def saveNodeKey(path: String, keyPair: KeyPair): IO[Unit] =
//    IO(File(path).createIfNotExists(createParents = true).overwrite(keyPair.secret.bytes.toHex))

//  def loadOrGenerateNodeKey(path: String): KeyPair =
//    IO(loadNodeKey(path)).attempt
//      .flatMap {
//        case Left(e) =>
//          for {
//            keyPair <- Signature[ECDSA].generateKeyPair[IO]()
//            _       <- saveNodeKey(path, keyPair)
//          } yield keyPair
//
//        case Right(nodeKey) =>
//          IO.pure(nodeKey)
//      }
//      .unsafeRunSync()
}
