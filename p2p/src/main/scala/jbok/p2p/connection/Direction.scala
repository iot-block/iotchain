package jbok.p2p.connection

sealed trait Direction
object Direction {
  case object Inbound  extends Direction
  case object Outbound extends Direction
}
