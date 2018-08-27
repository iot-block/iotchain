package jbok.examples.hg

import cats.effect.IO
import cats.implicits._
import jbok.JbokSpec
import jbok.common.GraphUtil
import jbok.persistent.InMemoryKeyValueDB
import jbok.testkit.Cast
import scalax.collection.io.dot.DotAttr
import scalax.collection.io.dot.implicits._

trait ConsensusFixture extends Generator {
  val db = InMemoryKeyValueDB[IO].unsafeRunSync()
  val pool = EventPool[IO].unsafeRunSync()
  val numEvents = 50
  val numMembers = 2
  val consensus = Consensus[IO](Config(numMembers), pool)
  val graph = genGraph(numEvents, numMembers)
  val events = graph.topologicalSort.right.get.toList.map(_.toOuter)

  events.traverse(consensus.insertEvent).unsafeRunSync()

  def color(x: Event) = x match {
    case _ if x.isOrdered              => "grey"
    case _ if x.isFamous == Some(true) => "green"
    case _ if x.isWitness              => "yellow"
    case _                             => "white"
  }

  def dot = GraphUtil.graphviz(
    graph,
    (x: Event) => {
      val o = pool.getEventByHash(x.hash).unsafeRunSync()
      List(
        DotAttr("label", s"${Cast.hash2name(o.body.creator)}-${o.info.round}-${o.body.index}"),
        DotAttr("style", "filled"),
        DotAttr("fillcolor", color(o))
      )
    }
  )
}

class ConsensusSpec extends JbokSpec {
  "graph" should {
    "be DAG" in new ConsensusFixture {
      graph.order shouldBe numEvents
      graph.isAcyclic shouldBe true
      graph.isConnected shouldBe true
    }

    "generate dot" ignore new ConsensusFixture {
      info(dot)
    }
  }

  "consensus" should {
    "get undivided" in new ConsensusFixture {
      val undivided = pool.getUndividedEvents.unsafeRunSync()
      undivided.foreach(_.isDivided shouldBe false)
      undivided.length shouldBe numEvents - 1
    }

    "divide rounds" in new ConsensusFixture {
      val undivided = pool.getUndividedEvents.unsafeRunSync()

      val divided = undivided.map(e => {
        val d = consensus.divideEvent(e).unsafeRunSync()
        val sp = pool.getEventByHash(d.sp).unsafeRunSync()
        val op = pool.getEventByHash(d.op).unsafeRunSync()
        val pr = math.max(sp.info.round, op.info.round)

        d.isWitness shouldBe d.round == sp.round + 1
        !d.isWitness shouldBe d.round == sp.round

        d.isWitness shouldBe d.isFamous == None
        !d.isWitness shouldBe d.isFamous == Some(false)

        val witnesses = pool.getWitnesses(d.round).unsafeRunSync()
        witnesses.foreach(w => {
          val nodes = GraphUtil.findAllPaths(graph, w, d, (y: Event) => Event.see(y, d)).flatMap(_.nodes.map(_.creator))
          Event.stronglySee(d, w, consensus.superMajority) shouldBe (nodes.size > consensus.superMajority)
        })
        d
      })

      divided.foreach(_.isDivided shouldBe true)
      divided.length shouldBe undivided.length
      pool.getUndividedEvents.unsafeRunSync().length shouldBe 0
    }

    "decideFame" in new ConsensusFixture {
      val undivided = pool.getUndividedEvents.unsafeRunSync()
      consensus.divideRounds(undivided).unsafeRunSync()
      val undecided = pool.getUndecidedRounds.unsafeRunSync()
      consensus.decideFame(undecided.min, undecided.max).unsafeRunSync()
      val stillUndecided = pool.getUndecidedRounds.unsafeRunSync()
      val decided = undecided.toSet.diff(stillUndecided.toSet)

      info(s"before: $undecided")
      info(s"after: $stillUndecided")

      decided.foreach(r => {
        val wits = pool.getWitnesses(r).unsafeRunSync()
        wits.forall(_.isDecided) shouldBe true
      })

      stillUndecided.foreach(r => {
        val wits = pool.getWitnesses(r).unsafeRunSync()
        wits.exists(!_.isDecided) shouldBe true
      })

      info(s"dot:\n$dot")
    }

    // find order
    "findOrder" in new ConsensusFixture {
      val undivided = pool.getUndividedEvents.unsafeRunSync()
      consensus.divideRounds(undivided).unsafeRunSync()

      val undecided = pool.getUndecidedRounds.unsafeRunSync()
      consensus.decideFame(undecided.min, undecided.max).unsafeRunSync()

      val unordered = pool.getUnorderedRounds.unsafeRunSync()
      consensus.findOrder(unordered.min, unordered.max).unsafeRunSync()
      val stillUnordered = pool.getUnorderedRounds.unsafeRunSync()
      val ordered = unordered.toSet.diff(stillUnordered.toSet)

      info(s"before: $unordered")
      info(s"after: $stillUnordered")

      val orderedEvents = ordered.flatMap(r => pool.getEvents(r).unsafeRunSync()).toList
      orderedEvents.forall(_.isOrdered) shouldBe true

      stillUnordered.foreach(r => {
        val events = pool.getEvents(r).unsafeRunSync()
        events.exists(!_.isOrdered) shouldBe true
      })
      info(s"dot:\n$dot")
      info(
        s"ordered: ${orderedEvents.sorted.map(x => (x.body.timestamp, x.info.roundReceived, x.info.consensusTimestamp)).mkString("\n")}")
    }
  }
}
