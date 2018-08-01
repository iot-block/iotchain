package jbok.core.models

case class BlockBody(transactionList: List[SignedTransaction], uncleNodesList: List[BlockHeader])
