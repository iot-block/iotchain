package jbok.evm
import jbok.JbokSpec
import better.files._

class ABISpec extends JbokSpec {
  val function =
    s"""
       |{
       |		"constant": true,
       |		"inputs": [],
       |		"name": "name",
       |		"outputs": [
       |			{
       |				"name": "",
       |				"type": "string"
       |			}
       |		],
       |		"payable": false,
       |		"stateMutability": "view",
       |		"type": "function"
       |}
     """.stripMargin

  val event =
    s"""
       |{
       |		"anonymous": false,
       |		"inputs": [
       |			{
       |				"indexed": true,
       |				"name": "from",
       |				"type": "address"
       |			},
       |			{
       |				"indexed": true,
       |				"name": "to",
       |				"type": "address"
       |			},
       |			{
       |				"indexed": false,
       |				"name": "value",
       |				"type": "uint256"
       |			}
       |		],
       |		"name": "Transfer",
       |		"type": "event"
       |}
     """.stripMargin

  "abi" should {
    "parse function" in {
      abi.parseFunction(function).isRight shouldBe true
      abi.parseFunction(function) shouldBe abi.parseDescription(function)
    }

    "parse event" in {
      abi.parseEvent(event).isRight shouldBe true
      abi.parseEvent(event) shouldBe abi.parseDescription(event)
    }

    "parse contract" in {
      val json = Resource.getAsString("abi.json")(DefaultCharset)
      val contract = abi.parseContract(json)
      contract.isRight shouldBe true
    }
  }
}
