package jbok.testkit

import scala.annotation.tailrec
import scalax.collection.GraphEdge.DiEdge
import scalax.collection.GraphPredef._
import scalax.collection._
import scalax.collection.mutable.{Graph => MGraph}

case class Metrics(order: Int)

object GraphGen {
  def randomGraph[N](genesis: N, metrics: Metrics)(f: MGraph[N, DiEdge] => Unit): Graph[N, DiEdge] = {
    @tailrec
    def gen(graph: MGraph[N, DiEdge]): MGraph[N, DiEdge] = {
      if (graph.nodes.size >= metrics.order) {
        graph
      } else {
        f(graph)
        gen(graph)
      }
    }

    gen(MGraph(genesis))
  }
}
