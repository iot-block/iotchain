package jbok.p2p

import org.scalatest.Matchers

class MultiAddressTest extends org.scalatest.FunSuite with Matchers {
  val succeeds = Seq(
    "/ip4/1.2.3.4",
    "/ip4/0.0.0.0",
    "/ip6/::1",
    "/ip6/2601:9:4f81:9700:803e:ca65:66e8:c21",
    "/onion/timaq4ygg2iegci7:1234",
    "/onion/timaq4ygg2iegci7:80/http",
    "/udp/0",
    "/tcp/0",
    "/sctp/0",
    "/udp/1234",
    "/tcp/1234",
    "/dns4/ipfs.io",
    "/dns6/ipfs.io",
    "/dnsaddr/ipfs.io",
    "/sctp/1234",
    "/udp/65535",
    "/tcp/65535",
    "/ipfs/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC",
    "/ipfs/zdpuAnNyxJR8tigBDe5FM2PfMq1u7fKSCsTmb4m3iJZcCq8yB",
    "/ipfs/zdpuAnNyxJR8tigBDe5FM2PfMq1u7fKSCsTmb4m3iJZcCq8yB/p2p-circuit",
    "/udp/1234/sctp/1234",
    "/udp/1234/udt",
    "/udp/1234/utp",
    "/tcp/1234/http",
    "/tcp/1234/https",
    "/tcp/1234/ws",
    "/ipfs/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC/tcp/1234",
    "/ip4/127.0.0.1/udp/1234",
    "/ip4/127.0.0.1/udp/0",
    "/ip4/127.0.0.1/tcp/1234",
    "/ip4/127.0.0.1/tcp/1234/",
    "/ip4/127.0.0.1/ipfs/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC",
    "/ip4/127.0.0.1/ipfs/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC/tcp/1234",
    "/unix/a/b/c/d/e",
    "/unix/stdio",
    "/ip4/1.2.3.4/tcp/80/unix/a/b/c/d/e/f",
    "/ip4/127.0.0.1/ipfs/QmcgpsyWgH8Y8ajJz1Cu72KnS5uo2Aa2LpzU7kinSupNKC/tcp/1234/unix/stdio"
  )

  test("decode from string") {
    succeeds.map(s => s -> MultiAddr.parseString(s)).foreach {
      case (str, result) =>
        result.right.get.toString shouldBe str.stripSuffix("/")
    }
  }

  test("encode to string") {
    val m1 = MultiAddr("ip4" -> "127.0.0.1", "tcp" -> "1234").toString
    val m2 = MultiAddr.tcp("127.0.01", 1234).toString
    val m3 = MultiAddr.tcp("::1", 1234).toString

    m1 shouldBe m2
    m2 shouldBe "/ip4/127.0.0.1/tcp/1234"
    m3 shouldBe "/ip6/0:0:0:0:0:0:0:1/tcp/1234"
  }
}
