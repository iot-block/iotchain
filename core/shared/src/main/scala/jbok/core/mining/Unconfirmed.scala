package jbok.core.mining

import jbok.core.models.{Block, Receipt}

case class Unconfirmed(block: Block, receipts: List[Receipt])
