package jbok.app

import java.net.URI

import jbok.JbokAsyncSpec

class JbokClientSpec extends JbokAsyncSpec {
  val uri = new URI("ws://localhost:8888")

  "JbokClient" should {
    "oho" in {
      for {
        client <- JbokClient(uri)
        api = client.api
        accounts <- api.listAccounts
        _ = println(accounts)
      } yield ()
    }
  }

}
