package jbok.rpc.json

import java.util.UUID

import jbok.rpc.json.JsonRPCMessage.RequestId

object RequestId {
  val Null: RequestId = ""

  def random: RequestId = UUID.randomUUID().toString
}
