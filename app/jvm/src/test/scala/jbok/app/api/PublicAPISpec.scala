package jbok.app.api

import jbok.JbokSpec

class PublicAPISpec extends JbokSpec{
  "MainAPI" should {
    "get bestBlockNumber" ignore {}

    "get version" ignore {}

    "getBlockTransactionCountByHash with None when the requested block isn't in the blockchain" ignore {}

    "getTransactionByBlockHashAndIndex" ignore {}

    "getBlockByNumBer" ignore {}

    "getBlockByHash" ignore {}

    "getUncleByBlockHashAndIndex" ignore {}

    "getUncleByBlockNumberAndIndex" ignore {}

    "return syncing info" ignore {}

    "accept submitted correct PoW" ignore {}

    "reject submitted correct PoW when header is no longer in cache" ignore {}

    "return average gas price" ignore {}

    "return account recent transactions in newest -> oldest order" ignore {}
  }

  override protected def beforeAll(): Unit = {}

  override protected def afterAll(): Unit = {}
}
