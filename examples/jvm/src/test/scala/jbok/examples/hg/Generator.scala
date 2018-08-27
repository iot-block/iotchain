package jbok.examples.hg

import jbok.codec.rlp.RlpCodec
import jbok.crypto._
import jbok.testkit.Cast
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef._
import scalax.collection.mutable.{Graph => MGraph}
import scodec.bits.ByteVector
import jbok.codec.rlp.codecs._

import scala.util.Random

trait Generator {
  def emptyEvent(sp: Event, op: Event, creator: ByteVector, timestamp: Long): Event = {
    val ts = math.max(sp.body.timestamp, op.body.timestamp) + 1L
    val body = EventBody(sp.hash, op.hash, creator, timestamp, sp.body.index + 1, Nil)
    val hash = RlpCodec.encode(body).require.bytes.kec256
    Event(body, hash)
  }

  def genGraph(
      size: Int,
      n: Int,
      g: MGraph[Event, DiEdge] = MGraph.empty,
      lastEvents: Map[ByteVector, Event] = Map.empty): Graph[Event, DiEdge] = {
    if (size <= 0) {
      g
    } else if (g.order == 0) {
      g += Consensus.genesis
      val creators = (1 to n).toList.map(i => Cast.name2hash(Cast.names(i)))
      val newEvents = creators.map(c => emptyEvent(Consensus.genesis, Consensus.genesis, c, g.nodes.length + 1L))
      newEvents.foreach(e => g += (Consensus.genesis ~> e))
      genGraph(size - (n + 1), n, g, newEvents.map(x => x.body.creator -> x).toMap)
    } else {
      val Vector(sender, receiver) = Random.shuffle(1 to n).toVector.take(2)
      val op = lastEvents(Cast.name2hash(Cast.names(sender)))
      val sp = lastEvents(Cast.name2hash(Cast.names(receiver)))
      val newEvent = emptyEvent(sp, op, sp.body.creator, g.nodes.length + 1L)
      g += (sp ~> newEvent, op ~> newEvent)
      genGraph(size - 1, n, g, lastEvents + (sp.body.creator -> newEvent))
    }
  }
}
