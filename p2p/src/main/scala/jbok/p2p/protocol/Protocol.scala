package jbok.p2p.protocol

import akka.stream.scaladsl.{BidiFlow, Flow, GraphDSL, Keep, Merge, Source, SourceQueueWithComplete}
import akka.stream.{FlowShape, OverflowStrategy}
import akka.util.ByteString

case class Protocol[M](codec: BidiFlow[ByteString, M, M, ByteString, Any], handler: Flow[M, M, Any]) {
  def flow(
      inputBufferSize: Int,
      inputOverflowStrategy: OverflowStrategy): Flow[ByteString, ByteString, SourceQueueWithComplete[M]] = {

    val flow = Flow.fromGraph(GraphDSL.create(Source.queue[M](inputBufferSize, inputOverflowStrategy)) {
      implicit b => source =>
        import GraphDSL.Implicits._
        val response = b.add(handler)
        val merge    = b.add(Merge[M](2, eagerComplete = true))
        response ~> merge
        source ~> merge
        FlowShape(response.in, merge.out)
    })

    codec.joinMat(flow)(Keep.right)
  }
}

object Protocol {
  val dummy = Protocol(Codec.dummy, Handler[ByteString] { case x => x })
}
