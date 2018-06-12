package jbok.common

import scalax.collection.Graph
import scalax.collection.GraphEdge.{DiEdge, UnDiEdge}
import scalax.collection.GraphPredef.EdgeLikeIn
import scalax.collection.io.dot._
import scalax.collection.io.dot.implicits._

import scala.collection.mutable

object GraphUtil {

  def findAllPaths[N, E[X] <: EdgeLikeIn[X]](
      g: Graph[N, E],
      source: N,
      target: N,
      breakOut: N => Boolean
  ): Set[g.Path] = {
    val s = g.get(source)
    val t = g.get(target)

    val found = mutable.Set[g.Path]()
    val queue = new mutable.Queue[g.Path]()

    queue += g.newPathBuilder(s).result()

    while (queue.nonEmpty) {
      val path = queue.dequeue()
      val last = path.nodes.last
      if (last == t) {
        found += path
      } else if (breakOut(last)) {
        //
      } else {
        last.diSuccessors.foreach(n => {
          val b = g.newPathBuilder(path.nodes.head)
          path.nodes.tail.foreach(b.add)
          b.add(n)
          val newPath = b.result()
          queue += newPath
        })
      }
    }

    found.toSet
  }

  def defaultNodeTransformer[N](x: N): List[DotAttr] = List(DotAttr("label", x.toString))

  def graphviz[N, E[X] <: EdgeLikeIn[X]](
      graph: Graph[N, E],
      nt: N => List[DotAttr] = defaultNodeTransformer[N] _): String = {
    val dotRoot = DotRootGraph(
      directed = true,
      id = None,
      attrStmts = List(DotAttrStmt(Elem.node, List(DotAttr("shape", "record"))))
    )

    def nodeTransformer(innerNode: Graph[N, E]#NodeT): Option[(DotGraph, DotNodeStmt)] = {
      Some((dotRoot, DotNodeStmt(innerNode.toString, nt(innerNode.toOuter))))
    }

    def edgeTransformer(innerEdge: Graph[N, E]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = innerEdge.edge match {
      case DiEdge(source, target) => Some((dotRoot, DotEdgeStmt(source.toString, target.toString, Nil)))
      case UnDiEdge(a, b) => Some((dotRoot, DotEdgeStmt(a.toString, b.toString, Nil)))
    }

    graph.toDot(dotRoot, edgeTransformer, cNodeTransformer = Some(nodeTransformer))
  }
}
