package jbok.p2p.address

import java.net.{Inet4Address, Inet6Address, InetAddress}

import cats.data.NonEmptyList
import fastparse.all._
import jbok.p2p.address.MultiAddr._

case class MultiAddr(addr: NonEmptyList[(Proto, Value)]) {
  override def toString =
    addr.collect {
      case (proto, value) if value.nonEmpty => s"/$proto/$value"
      case (proto, _) => s"/$proto"
    }.mkString

  def atop(other: MultiAddr): MultiAddr = this.copy(addr = other.addr.concatNel(this.addr))
}

object MultiAddr {
  type Proto = String
  type Value = String

  val terminalProtocols = Seq(
    "http",
    "https",
    "ws",
    "unix",
    "ipfs",
    "udt",
    "utp"
  )

  def apply(head: (Proto, Value), tail: (Proto, Value)*): MultiAddr =
    MultiAddr(NonEmptyList.of(head, tail: _*))

  def parseString(s: String): Either[Parsed.Failure, MultiAddr] = {
    val nonSlash = P(CharPred(_ != '/')).rep(1)
    val terminal = P("/" ~ StringIn(terminalProtocols: _*).! ~ ("/" ~ AnyChar.rep(1).!).?.map(_.getOrElse("")))
    val nonTerminal = P("/" ~ !StringIn(terminalProtocols: _*) ~ nonSlash.! ~ "/" ~ nonSlash.!)

    val multiAddr = P(
      Start
        ~ nonTerminal.rep(exactly = 0) ~ terminal.rep(exactly = 1) | (nonTerminal.rep(1) ~ terminal.rep(max = 1))
        ~ End
    )

    multiAddr.parse(s.stripSuffix("/")) match {
      case Parsed.Success((xs1, xs2), _) =>
        val nel = NonEmptyList.fromListUnsafe((xs1 ++ xs2).toList)
        Right(MultiAddr(nel))
      case e: Parsed.Failure => Left(e)
    }
  }

  def ip4(inet4Address: Inet4Address): MultiAddr =
    MultiAddr("ip4" -> inet4Address.getHostAddress)

  def ip6(inet6Address: Inet6Address): MultiAddr =
    MultiAddr("ip6" -> inet6Address.getHostAddress)

  def ip(host: String): MultiAddr = {
    val addr = InetAddress.getByName(host)
    addr match {
      case x: Inet4Address => ip4(x)
      case x: Inet6Address => ip6(x)
    }
  }

  def tcp(host: String, port: Int) =
    MultiAddr("tcp" -> port.toString).atop(ip(host))

  def udp(host: String, port: Int) =
    MultiAddr("udp" -> port.toString).atop(ip(host))
}
