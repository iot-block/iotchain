package jbok.examples.hg

import cats.Id
import jbok.PropertyTest
import jbok.common.GraphUtil
import jbok.core.store.KVStore
import jbok.crypto.hashing.MultiHash
import jbok.testkit.Cast

import scalax.collection.io.dot.DotAttr
import scalax.collection.io.dot.implicits._

class HGTest extends PropertyTest with HGGen {
  val events = implicitly[KVStore[Id, MultiHash, Event]]
  val rounds = implicitly[KVStore[Id, Round, RoundInfo]]
  val pool = new Pool[Id](events, rounds)

  val numEvents = 20
  val numMembers = 2
  val hg = new HG[Id](pool, HGConfig(numMembers)) {}
  val graph = genGraph(numEvents, numMembers)
  def dot = GraphUtil.graphviz(
    graph,
    (x: Event) => {
      val o = pool.getEvent(x.hash)
      List(
        DotAttr("label", s"${Cast.hash2name(o.body.creator)}-${o.round}-${o.body.index}"),
        DotAttr("style", "filled"),
        DotAttr("fillcolor", if (o.isFamous == Some(true)) "green" else if (o.isWitness) "red" else "white")
      )
    }
  )

  test("gen graph") {
    graph.order shouldBe numEvents
    graph.isAcyclic shouldBe true
  }

  test("consensus") {
    val events = graph.topologicalSort.right.get.toList.map(_.toOuter)
    events.foreach(hg.insertEvent)

    val undivided = hg.pool.getUndividedEvents
    undivided.length shouldBe numEvents - 1

    // divide rounds
    val divided = undivided.map(e => {
      val d = hg.divide(e)
      val sp = pool.getEvent(d.sp)
      val op = pool.getEvent(d.op)
      val pr = math.max(sp.round, op.round)

      d.isWitness shouldBe d.round == sp.round + 1
      !d.isWitness shouldBe d.round == sp.round

      d.isWitness shouldBe d.isFamous == None
      !d.isWitness shouldBe d.isFamous == Some(false)

      val witnesses = hg.pool.getWitnessesAt(d.round)
      witnesses.foreach(w => {
        val nodes = GraphUtil.findAllPaths(graph, w, d).flatMap(_.nodes.map(_.creator))
        hg.stronglySee(d, w) shouldBe (nodes.size > hg.superMajority())
      })
      d
    })

    divided.length shouldBe undivided.length
    hg.pool.getUndividedEvents.length shouldBe 0

    // decide fame
    val undecided = hg.pool.undecidedRounds
    hg.decideFame()
    val stillUndecided = hg.pool.undecidedRounds

    (undecided.toSet diff stillUndecided.toSet).foreach(r => {
      val wits = hg.pool.getWitnessesAt(r)
      wits.forall(_.isDecided) shouldBe true
    })

    stillUndecided.foreach(r => {
      val wits = hg.pool.getWitnessesAt(r)
      wits.exists(!_.isDecided) shouldBe true
    })

    // find order
    val unordered = hg.pool.unorderedRounds
    hg.findOrder()
    val stillUnordered = hg.pool.unorderedRounds

    (unordered.toSet diff stillUnordered.toSet).foreach(r => {
      val events = hg.pool.getEventsAt(r)
      events.forall(_.isOrdered) shouldBe true
    })

    stillUnordered.foreach(r => {
      val events = hg.pool.getEventsAt(r)
      events.exists(!_.isOrdered) shouldBe true
    })
  }
}
