package jbok.p2p.protocol

import akka.stream.scaladsl.Flow

object Handler {
  def apply[M](pf: PartialFunction[M, M]): Flow[M, M, Any] = Flow[M].map(pf)
}
