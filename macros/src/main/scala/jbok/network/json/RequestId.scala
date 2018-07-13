package jbok.network.json

import java.util.UUID

import jbok.network.json.JsonRPCMessage.RequestId

object RequestId {
  val Null: RequestId = ""

  def random: RequestId = UUID.randomUUID().toString
}
