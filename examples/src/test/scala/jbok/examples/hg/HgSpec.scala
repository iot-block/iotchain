package jbok.examples.hg

import cats.Id
import jbok.JbokSpec
import jbok.common.GraphUtil
import jbok.core.store.KVStore
import jbok.crypto.hashing.MultiHash
import jbok.testkit.Cast

import scalax.collection.io.dot.DotAttr
import scalax.collection.io.dot.implicits._

class HgSpec extends JbokSpec with HgGen {
  val events = implicitly[KVStore[Id, MultiHash, Event]]
  val rounds = implicitly[KVStore[Id, Round, RoundInfo]]
  implicit val pool = new Pool[Id](events, rounds)

  val numEvents = 50
  val numMembers = 2
  val hg = new HG[Id](HGConfig(numMembers))
  val graph = genGraph(numEvents, numMembers)
  def color(x: Event) = x match {
    case _ if x.isOrdered => "grey"
    case _ if x.isFamous == Some(true) => "green"
    case _ if x.isWitness => "yellow"
    case _ => "white"
  }
  def dot = GraphUtil.graphviz(
    graph,
    (x: Event) => {
      val o = pool.getEvent(x.hash)
      List(
        DotAttr("label", s"${Cast.hash2name(o.body.creator)}-${o.info.round}-${o.body.index}"),
        DotAttr("style", "filled"),
        DotAttr("fillcolor", color(o))
      )
    }
  )

  "graph" should {
    "be DAG" in {
      graph.order shouldBe numEvents
      graph.isAcyclic shouldBe true
      graph.isConnected shouldBe true
    }

    "generate dot" in {
      info(dot)
    }
  }

  "consensus" when {
    val events = graph.topologicalSort.right.get.toList.map(_.toOuter)
    events.foreach(hg.insertEvent)

    val undivided = hg.undividedEvents

    "undivided" in {
      undivided.foreach(_.isDivided shouldBe false)
      undivided.length shouldBe numEvents - 1
    }

    "divideRounds" in {
      val divided = undivided.map(e => {
        val d = hg.divideEvent(e)
        val sp = hg.event(d.sp)
        val op = hg.event(d.op)
        val pr = math.max(sp.info.round, op.info.round)

        d.isWitness shouldBe d.round == sp.round + 1
        !d.isWitness shouldBe d.round == sp.round

        d.isWitness shouldBe d.isFamous == None
        !d.isWitness shouldBe d.isFamous == Some(false)

        val witnesses = hg.witnessesAt(d.round)
        witnesses.foreach(w => {
          val nodes = GraphUtil.findAllPaths(graph, w, d, (y: Event) => Event.see(y, d)).flatMap(_.nodes.map(_.creator))
          Event.stronglySee(d, w, hg.superMajority) shouldBe (nodes.size > hg.superMajority)
        })
        d
      })

      divided.foreach(_.isDivided shouldBe true)
      divided.length shouldBe undivided.length
      hg.undividedEvents.length shouldBe 0
    }

    "decideFame" in {
      val undecided = hg.undecidedRounds
      hg.decideFame(undecided.min, undecided.max)
      val stillUndecided = hg.undecidedRounds
      val decided = undecided.toSet.diff(stillUndecided.toSet)

      info(s"before: $undecided")
      info(s"after: $stillUndecided")

      decided.foreach(r => {
        val wits = hg.witnessesAt(r)
        wits.forall(_.isDecided) shouldBe true
      })

      stillUndecided.foreach(r => {
        val wits = hg.witnessesAt(r)
        wits.exists(!_.isDecided) shouldBe true
      })

      info(s"dot:\n$dot")
    }

    // find order
    "findOrder" in {
      val unordered = hg.unorderedRounds
      hg.findOrder(unordered.min, unordered.max)
      val stillUnordered = hg.unorderedRounds
      val ordered = unordered.toSet.diff(stillUnordered.toSet)

      info(s"before: $unordered")
      info(s"after: $stillUnordered")

      val orderedEvents = ordered.flatMap(hg.eventsAt).toList
      orderedEvents.forall(_.isOrdered) shouldBe true

      stillUnordered.foreach(r => {
        val events = hg.eventsAt(r)
        events.exists(!_.isOrdered) shouldBe true
      })
      info(s"dot:\n$dot")
      info(
        s"ordered: ${orderedEvents.sorted.map(x => (x.body.timestamp, x.info.roundReceived, x.info.consensusTimestamp)).mkString("\n")}")
    }
  }
}
