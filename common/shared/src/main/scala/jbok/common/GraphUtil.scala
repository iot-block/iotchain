package jbok.common

import scalax.collection.Graph
import scalax.collection.GraphEdge.{DiEdge, UnDiEdge}
import scalax.collection.GraphPredef.EdgeLikeIn
import scalax.collection.io.dot._
import scalax.collection.io.dot.implicits._

object GraphUtil {
  @SuppressWarnings(Array("org.wartremover.warts.ToString"))
  def defaultNodeTransformer[N](x: N): List[DotAttr] = List(DotAttr("label", x.toString))

  def graphviz[N, E[X] <: EdgeLikeIn[X]](graph: Graph[N, E],
                                         nt: N => List[DotAttr] = defaultNodeTransformer[N] _): String = {
    val dotRoot = DotRootGraph(
      directed = true,
      id = None,
      attrStmts = List(DotAttrStmt(Elem.node, List(DotAttr("shape", "record"))))
    )

    def nodeTransformer(innerNode: Graph[N, E]#NodeT): Option[(DotGraph, DotNodeStmt)] =
      Some((dotRoot, DotNodeStmt(innerNode.toString, nt(innerNode.toOuter))))

    def edgeTransformer(innerEdge: Graph[N, E]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = innerEdge.edge match {
      case DiEdge(source, target) => Some((dotRoot, DotEdgeStmt(source.toString, target.toString, Nil)))
      case UnDiEdge(a, b)         => Some((dotRoot, DotEdgeStmt(a.toString, b.toString, Nil)))
    }

    graph.toDot(dotRoot, edgeTransformer, cNodeTransformer = Some(nodeTransformer))
  }
}
