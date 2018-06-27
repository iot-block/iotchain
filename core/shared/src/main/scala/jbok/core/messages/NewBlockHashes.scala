package jbok.core.messages

case class NewBlockHashes(hashes: Seq[BlockHash]) extends Message
