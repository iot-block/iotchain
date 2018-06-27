package jbok.core.messages

import jbok.core.models.Receipt

case class Receipts(receiptsForBlocks: List[List[Receipt]]) extends Message
