package jbok.p2p.connection

import java.net.InetSocketAddress

case class Connection(remote: InetSocketAddress, local: InetSocketAddress, direction: Direction)
