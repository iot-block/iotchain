package jbok.app.service

object models {
  final case class Block(
      blockHash: String,
      parentHash: String, // B32 pre
      ommersHash: String, // B32 body
      beneficiary: String, // B20 pre
      stateRoot: String, // B32 exec
      transactionsRoot: String, // B32 body
      receiptsRoot: String, // B32 exec
      logsBloom: String, // B256 post exec
      difficulty: String, // consensus
      blockNumber: Long,
      gasLimit: String, // consensus field
      gasUsed: String, // post
      unixTimestamp: Long, // pre
      extra: String
  )

  final case class Transaction(
      txHash: String,
      nonce: Int,
      fromAddress: String,
      toAddress: String,
      value: String,
      payload: String,
      v: String,
      r: String,
      s: String,
      gasUsed: String,
      gasPrice: String,
      blockNumber: Long,
      blockHash: String,
      location: Int
  )
}
