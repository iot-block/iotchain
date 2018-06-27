package jbok.core.messages

import jbok.core.models.BlockHeader

case class BlockHeaders(headers: Seq[BlockHeader]) extends Message
