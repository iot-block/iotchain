package jbok.p2p.connection

import java.net.InetSocketAddress

case class Connection(remote: InetSocketAddress, local: InetSocketAddress, direction: Direction) {
  def isDup(other: Connection): Boolean =
    (this.remote == other.local) && (this.local == other.remote)
}
