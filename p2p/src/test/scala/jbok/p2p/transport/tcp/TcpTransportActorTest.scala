package jbok.p2p.transport.tcp

import java.net.InetSocketAddress

import akka.actor.Props
import akka.stream.ActorMaterializer
import akka.testkit.{TestActorRef, TestProbe}
import jbok.p2p.AkkaStreamTest
import jbok.p2p.protocol.Protocol
import jbok.p2p.transport.{TransportEvent, TransportOp}

class TcpTransportActorTest extends AkkaStreamTest {
  implicit val fm = ActorMaterializer()

  test("tcp transport") {
    val protocol = Protocol.dummy

    val serverParent = TestProbe("serverParent")
    val clientParent = TestProbe("clientParent")

    val server = TestActorRef(Props(new TcpTransportActor(protocol)), serverParent.ref, "tcp-server")
    val client = TestActorRef(Props(new TcpTransportActor(protocol)), clientParent.ref, "tcp-client")

    // listen
    val bind = new InetSocketAddress("localhost", 10001)
    server ! TransportOp.Bind(bind)
    serverParent.expectMsg(TransportEvent.Bound(bind))

    // dial
    client ! TransportOp.Dial(bind)
    serverParent.expectMsgPF() {
      case TransportEvent.Connected(conn1, _) =>
        clientParent.expectMsgPF() {
          case TransportEvent.Connected(conn2, _) =>
            conn1.isDup(conn2) shouldBe true
        }
    }

    // close
    client ! TransportOp.Close(None, None) // close all
    clientParent.expectMsgPF() {
      case TransportEvent.Disconnected(conn1, _) =>
        serverParent.expectMsgPF() {
          case TransportEvent.Disconnected(conn2, _) =>
            conn1.isDup(conn2) shouldBe true
        }
    }
  }
}
