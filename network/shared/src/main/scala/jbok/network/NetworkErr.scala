package jbok.network

sealed abstract class NetworkErr(reason: String) extends Exception(reason)
object NetworkErr {
  case object Timeout       extends NetworkErr("timeout")
  case object AlreadyClosed extends NetworkErr("reading data from an already closed connection")
}
