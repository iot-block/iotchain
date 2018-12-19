package jbok.app.api

trait AdminAPI[F[_]] {
  def stop: F[Unit]

  def peerNodeUri: F[String]

  def addPeer(peerNodeUri: String): F[Unit]

  def dropPeer(peerNodeUri: String): F[Unit]
}
