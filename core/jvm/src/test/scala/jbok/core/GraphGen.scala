package jbok.core

import scalax.collection.GraphEdge.{DiEdge, UnDiEdge}
import scalax.collection.GraphPredef._
import scalax.collection._
import scalax.collection.generator.RandomGraph.Metrics
import scalax.collection.generator._
import scalax.collection.mutable.{Graph => MGraph}

import scala.annotation.tailrec
import scala.reflect.ClassTag

object GraphGen {
  def metrics[N: ClassTag](n: Int, dr: Range, gen: () => N): Metrics[N] = new Metrics[N] {
    override def nodeGen = gen()

    override def order = n

    override def nodeDegrees = NodeDegreeRange(dr.start, dr.end)

    override def connected = true
  }

  def randomUnDiGraph[N: ClassTag](n: Int, dr: Range, gen: () => N): RandomGraph[N, UnDiEdge, Graph] =
    RandomGraph[N, UnDiEdge, Graph](Graph, metrics[N](n, dr, gen), Set(UnDiEdge))

  def randomDiGraph[N: ClassTag](n: Int, dr: Range, gen: () => N): RandomGraph[N, DiEdge, Graph] =
    RandomGraph[N, DiEdge, Graph](Graph, metrics[N](n, dr, gen), Set(DiEdge))

  @tailrec
  def ring[N: ClassTag](nodes: List[N], start: N, g: MGraph[N, DiEdge] = MGraph.empty[N, DiEdge]): Graph[N, DiEdge] = nodes match {
    case h1 :: h2 :: tail =>
      g += h1 ~> h2
      ring[N](h2 :: tail, start, g)
    case head :: Nil =>
      g += head ~> start
    case _ => g
  }
}
