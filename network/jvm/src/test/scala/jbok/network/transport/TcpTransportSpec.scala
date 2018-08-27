package jbok.network.transport

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.UUID

import cats.effect.IO
import fs2._
import jbok.JbokSpec
import jbok.network.common.{RequestId, RequestMethod}
import jbok.network.execution._
import scodec.Codec
import scodec.codecs._

class TcpTransportSpec extends JbokSpec {
  case class Data(id: String, topic: String, data: String)
  object Data {
    def apply(data: String): Data = {
      val uuid = UUID.randomUUID().toString
      Data(uuid, "", data)
    }
  }

  implicit val I = new RequestId[Data] {
    override def id(a: Data): Option[String] = Some(a.id)
  }

  implicit val M: RequestMethod[Data] = new RequestMethod[Data] {
    override def method(a: Data): Option[String] = Some(a.topic)
  }

  implicit val codecString: Codec[String] = variableSizeBytes(uint16, string(StandardCharsets.US_ASCII))
  implicit val codecData: Codec[Data]     = (codecString :: codecString :: codecString).as[Data]
  val bind1                               = new InetSocketAddress("localhost", 10001)
  val bind2                               = new InetSocketAddress("localhost", 10002)

  def f(d: Data): Stream[IO, Data] =
    if (d.data.startsWith("hello")) {
      Stream.empty.covary[IO]
    } else {
      Stream(d.copy(data = s"hello, ${d.data}")).covary[IO]
    }
  val pipe: Pipe[IO, Data, Data] = _.flatMap(f)

  val transport1 = TcpTransport[IO, Data](pipe).unsafeRunSync()
  val transport2 = TcpTransport[IO, Data](pipe).unsafeRunSync()

  "TcpTransport" should {
    "request" in {
      val req = Data("oho")
      val p = for {
        resp <- transport1.request(bind2, req)
        _ = resp shouldBe Some(req.copy(data = s"hello, ${req.data}"))
      } yield ()
      p.unsafeRunSync()
    }

    "subscribe" in {
      val d = Data("oho")
      val p = for {
        _   <- transport1.write(bind2, d)
        list <- transport2.subscribe().take(1).compile.toList
        _ = list.last shouldBe a[TransportEvent.Received[_]]
      } yield ()
      p.unsafeRunSync()
    }

    "broadcast" in {
      val d = Data("oho")
      val p = for {
        _ <- transport1.broadcast(d)
        _ <- transport1.broadcast(d)
        list <- transport2.subscribe().take(2).compile.toList
        _ = list.last shouldBe a[TransportEvent.Received[_]]
      } yield ()
      p.unsafeRunSync()
    }
  }

  override protected def beforeAll(): Unit = {
    transport2.listen(bind2).unsafeRunSync()
    transport1.listen(bind1).unsafeRunSync()
    Thread.sleep(2000)
    transport1.connect(bind2).unsafeRunSync()
    Thread.sleep(2000)
  }

  override protected def afterAll(): Unit = {
    transport1.stop.unsafeRunSync()
    transport2.stop.unsafeRunSync()
  }
}
