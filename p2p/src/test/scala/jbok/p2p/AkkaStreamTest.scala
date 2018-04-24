package jbok.p2p

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, FunSuiteLike, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

abstract class AkkaStreamTest
    extends TestKit(ActorSystem("test"))
    with ImplicitSender
    with FunSuiteLike
    with Matchers
    with BeforeAndAfterAll {

  def await[A](future: Future[A]): A =
    Await.result(future, 5.seconds)

  override protected def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)
}
