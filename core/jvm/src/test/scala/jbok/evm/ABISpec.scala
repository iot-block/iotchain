package jbok.evm
import jbok.JbokSpec
import better.files._
import jbok.evm.abi.Param
import io.circe._
import io.circe.parser._
import scodec.bits._

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

  val contractABI =
    s"""
       |[
       |	{
       |		"constant": false,
       |		"inputs": [],
       |		"name": "getValues",
       |		"outputs": [
       |			{
       |				"name": "",
       |				"type": "uint256"
       |			}
       |		],
       |		"payable": false,
       |		"stateMutability": "nonpayable",
       |		"type": "function"
       |	},
       |	{
       |		"constant": true,
       |		"inputs": [
       |			{
       |				"name": "",
       |				"type": "uint256"
       |			}
       |		],
       |		"name": "values",
       |		"outputs": [
       |			{
       |				"name": "",
       |				"type": "uint256"
       |			}
       |		],
       |		"payable": false,
       |		"stateMutability": "view",
       |		"type": "function"
       |	},
       |	{
       |		"constant": false,
       |		"inputs": [
       |			{
       |				"components": [
       |					{
       |						"name": "a",
       |						"type": "uint256"
       |					},
       |					{
       |						"name": "b",
       |						"type": "uint256[]"
       |					},
       |					{
       |						"components": [
       |							{
       |								"name": "x",
       |								"type": "uint256"
       |							},
       |							{
       |								"name": "y",
       |								"type": "uint256"
       |							}
       |						],
       |						"name": "c",
       |						"type": "tuple[]"
       |					}
       |				],
       |				"name": "s",
       |				"type": "tuple"
       |			},
       |			{
       |				"components": [
       |					{
       |						"name": "x",
       |						"type": "uint256"
       |					},
       |					{
       |						"name": "y",
       |						"type": "uint256"
       |					}
       |				],
       |				"name": "t",
       |				"type": "tuple"
       |			},
       |			{
       |				"name": "a",
       |				"type": "uint256"
       |			}
       |		],
       |		"name": "f",
       |		"outputs": [
       |			{
       |				"name": "",
       |				"type": "uint256"
       |			}
       |		],
       |		"payable": false,
       |		"stateMutability": "nonpayable",
       |		"type": "function"
       |	},
       |	{
       |		"constant": false,
       |		"inputs": [
       |			{
       |				"name": "value",
       |				"type": "uint256"
       |			}
       |		],
       |		"name": "storeValue",
       |		"outputs": [],
       |		"payable": false,
       |		"stateMutability": "nonpayable",
       |		"type": "function"
       |	},
       |	{
       |		"constant": false,
       |		"inputs": [
       |			{
       |				"name": "initial",
       |				"type": "uint256[]"
       |			}
       |		],
       |		"name": "getValue",
       |		"outputs": [
       |			{
       |				"name": "",
       |				"type": "uint256"
       |			}
       |		],
       |		"payable": false,
       |		"stateMutability": "nonpayable",
       |		"type": "function"
       |	}
       |]
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
      val json     = Resource.getAsString("abi.json")(DefaultCharset)
      val contract = abi.parseContract(json)
      contract.isRight shouldBe true
    }

    "parse function and getByteCode" in {
      val functionABI =
        """
          |{
          |		"constant": false,
          |		"inputs": [
          |			{
          |				"name": "_from",
          |				"type": "address"
          |			},
          |			{
          |				"name": "_to",
          |				"type": "address"
          |			},
          |			{
          |				"name": "_value",
          |				"type": "uint256"
          |			}
          |		],
          |		"name": "transferFrom",
          |		"outputs": [
          |			{
          |				"name": "success",
          |				"type": "bool"
          |			}
          |		],
          |		"payable": false,
          |		"stateMutability": "nonpayable",
          |		"type": "function"
          |	}
        """.stripMargin
      val fpf = abi.parseFunction(functionABI)
      fpf.isRight shouldBe true
      val f = fpf.right.get
      val result = f.getByteCode(
        "[\"1234567890123456789012345678901234567890\", \"1234567890123456789012345678901234567890\", 1234567890]")
      result.isRight shouldBe true
      val encoded = result.right.get
      encoded shouldBe
        hex"23b872dd0000000000000000000000001234567890123456789012345678901234567890000000000000000000000000123456789012345678901234567890123456789000000000000000000000000000000000000000000000000000000000499602d2"

    }
  }

  "abi.encodePrimaryType" should {
    "encode int32" in {
      val inputs  = Param("a", "int32", None)
      val params1 = parse("-2").getOrElse(Json.Null)
      val result1 = abi.encodePrimaryType(inputs, params1)
      result1.isRight shouldBe true
      val (isDynamic1, encoded1) = result1.right.get
      isDynamic1 shouldBe false
      encoded1 shouldBe List(hex"0xfffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe")

      val params2 = parse("2").getOrElse(Json.Null)
      val result2 = abi.encodePrimaryType(inputs, params2)
      result2.isRight shouldBe true
      val (isDynamic2, encoded2) = result2.right.get
      isDynamic2 shouldBe false
      encoded2 shouldBe List(hex"0x0000000000000000000000000000000000000000000000000000000000000002")
    }

    "encode int256" in {
      val inputs  = Param("a", "int256", None)
      val params1 = parse("-19999999999999999999999999999999999999999999999999999999999999").getOrElse(Json.Null)
      val result1 = abi.encodePrimaryType(inputs, params1)
      result1.isRight shouldBe true
      val (isDynamic1, encoded1) = result1.right.get
      isDynamic1 shouldBe false
      encoded1 shouldBe List(hex"0xfffffffffffff38dd0f10627f5529bdb2c52d4846810af0ac000000000000001")

      val params2 = parse("100000000000000000000000000000000000000000000000000000000000").getOrElse(Json.Null)
      val result2 = abi.encodePrimaryType(inputs, params2)
      result2.isRight shouldBe true

      val (isDynamic2, encoded2) = result2.right.get
      isDynamic2 shouldBe false
      encoded2 shouldBe List(hex"0x000000000000000fee50b7025c36a0802f236d04753d5b48e800000000000000")
    }

    "encode bool" in {
      val inputs  = Param("a", "bool", None)
      val params1 = parse("true").getOrElse(Json.Null)
      val result1 = abi.encodePrimaryType(inputs, params1)
      result1.isRight shouldBe true
      val (isDynamic1, encoded1) = result1.right.get
      isDynamic1 shouldBe false
      encoded1 shouldBe List(hex"0x0000000000000000000000000000000000000000000000000000000000000001")

      val params2 = parse("false").getOrElse(Json.Null)
      val result2 = abi.encodePrimaryType(inputs, params2)
      result2.isRight shouldBe true
      val (isDynamic2, encoded2) = result2.right.get
      isDynamic2 shouldBe false
      encoded2 shouldBe List(hex"0x0000000000000000000000000000000000000000000000000000000000000000")
    }

    "encode address" in {
      val inputs = Param("a", "address", None)
      val params = parse("\"56d91a95B623F8900cAAafF11f586D4D54829c53\"").getOrElse(Json.Null)
      val result = abi.encodePrimaryType(inputs, params)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe false
      encoded shouldBe List(hex"0x00000000000000000000000056d91a95B623F8900cAAafF11f586D4D54829c53")
    }

    "encode string" in {
      val inputs = Param("a", "string", None)
      val params = parse(
        "\" hello world hello world hello world hello world  hello world hello world hello world hello world  hello world hello world hello world hello world hello world hello world hello world hello world\"")
        .getOrElse(Json.Null)
      val result = abi.encodePrimaryType(inputs, params)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe true
      encoded shouldBe List(
        hex"00000000000000000000000000000000000000000000000000000000000000c2",
        hex"2068656c6c6f20776f726c642068656c6c6f20776f726c642068656c6c6f2077",
        hex"6f726c642068656c6c6f20776f726c64202068656c6c6f20776f726c64206865",
        hex"6c6c6f20776f726c642068656c6c6f20776f726c642068656c6c6f20776f726c",
        hex"64202068656c6c6f20776f726c642068656c6c6f20776f726c642068656c6c6f",
        hex"20776f726c642068656c6c6f20776f726c642068656c6c6f20776f726c642068",
        hex"656c6c6f20776f726c642068656c6c6f20776f726c642068656c6c6f20776f72",
        hex"6c64000000000000000000000000000000000000000000000000000000000000"
      )
    }

    "encode bytes" in {
      val inputs = Param("a", "bytes", None)
      val params = parse("\"01234567890123456789012345678901234567890123456789012345678901234567890123456789\"")
        .getOrElse(Json.Null)
      val result = abi.encodePrimaryType(inputs, params)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000028",
        hex"0123456789012345678901234567890123456789012345678901234567890123",
        hex"4567890123456789000000000000000000000000000000000000000000000000"
      )
    }
  }

  "abi.encodeInput" should {
    "encode uint256[]" in {
      val inputs = Param("a", "uint256[]", None)
      val param  = parse("[[1,2]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0x0000000000000000000000000000000000000000000000000000000000000020",
        hex"0x0000000000000000000000000000000000000000000000000000000000000002",
        hex"0x0000000000000000000000000000000000000000000000000000000000000001",
        hex"0x0000000000000000000000000000000000000000000000000000000000000002"
      )
    }

    "encode uint256[2]" in {
      val inputs = Param("a", "uint256[2]", None)
      val param  = parse("[[1,2]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)

      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe false
      encoded shouldBe List(
        hex"0x0000000000000000000000000000000000000000000000000000000000000001",
        hex"0x0000000000000000000000000000000000000000000000000000000000000002"
      )
    }

    "encode uint256[][]" in {
      val inputs = Param("a", "uint256[][]", None)
      val param  = parse("[[[1,2], [3,4]]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get

      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000020",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000040",
        hex"00000000000000000000000000000000000000000000000000000000000000a0",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"0000000000000000000000000000000000000000000000000000000000000004"
      )
    }

    "encode uint256[2][]" in {
      val inputs = Param("a", "uint256[2][]", None)
      val param  = parse("[[[1,2], [3,4]]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get

      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000020",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"0000000000000000000000000000000000000000000000000000000000000004"
      )
    }

    "encode dynamic tuple" in {
      val inputs = Param("a",
                         "tuple",
                         Some(
                           List(
                             Param("b", "uint256", None),
                             Param("c", "uint256[]", None)
                           )))
      val param = parse("[[1, [2,3]]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)
      result.isRight shouldBe true

      val (isDynamic, encoded) = result.right.get

      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000020",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000040",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000003"
      )
    }

    "encode static tuple" in {
      val inputs = Param("a",
                         "tuple",
                         Some(
                           List(
                             Param("b", "uint256", None),
                             Param("c", "bool", None)
                           )))
      val param = parse("[[1, true]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)
      result.isRight shouldBe true

      val (isDynamic, encoded) = result.right.get

      isDynamic shouldBe false
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000001"
      )
    }

    "encode tuple[]" in {
      val inputs = Param("a",
                         "tuple[]",
                         Some(
                           List(
                             Param("b", "string", None),
                             Param("b", "uint256[]", None)
                           )))
      val param = parse("[[[\"hello iot chain!\", [1, 0, 2, 4]]]]").getOrElse(Json.Null)

      val result = abi.encodeInputs(List(inputs), param)
      result.isRight shouldBe true

      val (isDynamic, encoded) = result.right.get

      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000020",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000020",
        hex"0000000000000000000000000000000000000000000000000000000000000040",
        hex"0000000000000000000000000000000000000000000000000000000000000080",
        hex"0000000000000000000000000000000000000000000000000000000000000010",
        hex"68656c6c6f20696f7420636861696e2100000000000000000000000000000000",
        hex"0000000000000000000000000000000000000000000000000000000000000004",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000000",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000004"
      )
    }

    "encode string[]" in {
      val inputs = Param("a", "string[]", None)
      val params = parse("[[\"one\", \"two\", \"three\"]]")
        .getOrElse(Json.Null)
      val result = abi.encodeInputs(List(inputs), params)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000020",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"0000000000000000000000000000000000000000000000000000000000000060",
        hex"00000000000000000000000000000000000000000000000000000000000000a0",
        hex"00000000000000000000000000000000000000000000000000000000000000e0",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"6f6e650000000000000000000000000000000000000000000000000000000000",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"74776f0000000000000000000000000000000000000000000000000000000000",
        hex"0000000000000000000000000000000000000000000000000000000000000005",
        hex"7468726565000000000000000000000000000000000000000000000000000000"
      )
    }

    "encode uint[][],string[]" in {
      val inputs = List(
        Param("a", "uint[][]", None),
        Param("b", "string[]", None)
      )
      val params = parse("[[[1, 2], [3]], [\"one\", \"two\", \"three\"]]").getOrElse(Json.Null)
      val result = abi.encodeInputs(inputs, params)
      result.isRight shouldBe true
      val (isDynamic, encoded) = result.right.get
      isDynamic shouldBe true
      encoded shouldBe List(
        hex"0000000000000000000000000000000000000000000000000000000000000040",
        hex"0000000000000000000000000000000000000000000000000000000000000140",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000040",
        hex"00000000000000000000000000000000000000000000000000000000000000a0",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000002",
        hex"0000000000000000000000000000000000000000000000000000000000000001",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"0000000000000000000000000000000000000000000000000000000000000060",
        hex"00000000000000000000000000000000000000000000000000000000000000a0",
        hex"00000000000000000000000000000000000000000000000000000000000000e0",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"6f6e650000000000000000000000000000000000000000000000000000000000",
        hex"0000000000000000000000000000000000000000000000000000000000000003",
        hex"74776f0000000000000000000000000000000000000000000000000000000000",
        hex"0000000000000000000000000000000000000000000000000000000000000005",
        hex"7468726565000000000000000000000000000000000000000000000000000000"
      )
    }
  }

  "abi.decodePrimaryType" should {
    "decode int32" in {
      val outputs = Param("a", "int32", None)
      val value   = hex"0000000000000000000000000000000000000000000000000000000000001234"
      val result  = abi.decodePrimaryType(outputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe Json.fromBigInt(4660)
    }

    "decode uint256" in {
      val outputs = Param("a", "uint256", None)
      val value   = hex"1234567890123456789012345678901234567890123456789012345678901234"
      val result  = abi.decodePrimaryType(outputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe
        Json.fromBigInt(BigInt("8234104122419153896766082834368325185836758793849283143825308940974890684980"))
    }

    "decode bool" in {
      val outputs = Param("a", "bool", None)
      val value   = hex"0000000000000000000000000000000000000000000000000000000000000001"
      val result  = abi.decodePrimaryType(outputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe Json.True
    }

    "decode address" in {
      val outputs = Param("a", "address", None)
      val value   = hex"000000000000000000000000ca35b7d915458ef540ade6068dfe2f44e8fa733c"
      val result  = abi.decodePrimaryType(outputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe Json.fromString("0xca35b7d915458ef540ade6068dfe2f44e8fa733c")
    }

    "decode string" in {
      val outputs = Param("a", "string", None)
      val value =
        hex"000000000000000000000000000000000000000000000000000000000000002568656c6c6f20776f726c64212068656c6c6f20626c6f636b636861696e2120e4bda0e5a5bd000000000000000000000000000000000000000000000000000000"
      val result = abi.decodePrimaryType(outputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe Json.fromString("hello world! hello blockchain! 你好")
    }

    "decode bytes" in {
      val outputs = Param("a", "bytes", None)
      val value =
        hex"000000000000000000000000000000000000000000000000000000000000002568656c6c6f20776f726c64212068656c6c6f20626c6f636b636861696e2120e4bda0e5a5bd000000000000000000000000000000000000000000000000000000"
      val result = abi.decodePrimaryType(outputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe
        Json.fromString("0x68656c6c6f20776f726c64212068656c6c6f20626c6f636b636861696e2120e4bda0e5a5bd")
    }
  }

  "abi.decodeOutputs" should {
    "decode uint256[]" in {
      val inputs = Param("a", "uint256[]", None)
      val param  = parse("[[3,4]]").getOrElse(Json.Null)
      val value =
        hex"0x0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000004"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode uint256[2]" in {
      val inputs = Param("a", "uint256[2]", None)
      val param  = parse("[[3,4]]").getOrElse(Json.Null)
      val value =
        hex"0x00000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000004"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode uint256[][]" in {
      val inputs = Param("a", "uint256[][]", None)
      val param  = parse("[[[1,2],[3,4]]]").getOrElse(Json.Null)
      val value =
        hex"0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000004"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode dynamic tuple" in {
      val inputs = Param("a",
                         "tuple",
                         Some(
                           List(
                             Param("b", "uint256", None),
                             Param("c", "uint256[]", None)
                           )))
      val param = parse("[[1, [2,3]]]").getOrElse(Json.Null)
      val value =
        hex"0x000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000003"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode static tuple" in {
      val inputs = Param("a",
                         "tuple",
                         Some(
                           List(
                             Param("b", "uint256", None),
                             Param("c", "bool", None)
                           )))
      val param = parse("[[1, true]]").getOrElse(Json.Null)
      val value =
        hex"0x00000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000001"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode tuple[]" in {
      val inputs = Param("a",
                         "tuple[]",
                         Some(
                           List(
                             Param("b", "string", None),
                             Param("b", "uint256[]", None)
                           )))
      val param = parse("[[[\"hello iot chain!\", [1, 0, 2, 4]]]]").getOrElse(Json.Null)
      val value =
        hex"0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000001068656c6c6f20696f7420636861696e210000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode string[]" in {
      val inputs = Param("a", "string[]", None)
      val param  = parse("[[\"one\", \"two\", \"three\"]]").getOrElse(Json.Null)
      val value =
        hex"0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000003000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000036f6e650000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000374776f000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000057468726565000000000000000000000000000000000000000000000000000000"

      val result = abi.decodeOutputs(List(inputs), value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }

    "decode uint[][], string[]" in {
      val inputs = List(
        Param("a", "uint[][]", None),
        Param("b", "string[]", None)
      )
      val param = parse("[[[1, 2], [3]], [\"one\", \"two\", \"three\"]]").getOrElse(Json.Null)
      val value =
        hex"0x000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000001400000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000100000000000000000000000000000000000000000000000000000000000000030000000000000000000000000000000000000000000000000000000000000003000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000a000000000000000000000000000000000000000000000000000000000000000e000000000000000000000000000000000000000000000000000000000000000036f6e650000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000374776f000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000057468726565000000000000000000000000000000000000000000000000000000"

      val result = abi.decodeOutputs(inputs, value)
      result.isRight shouldBe true
      result.right.get shouldBe param
    }
  }

  "abi.encode and decode" should {
    "encode inputs" in {
      val functionABI =
        """
          |{
          |		"constant": false,
          |		"inputs": [
          |			{
          |				"components": [
          |					{
          |						"name": "a",
          |						"type": "uint256"
          |					},
          |					{
          |						"name": "b",
          |						"type": "uint256[]"
          |					},
          |					{
          |						"components": [
          |							{
          |								"name": "x",
          |								"type": "uint256"
          |							},
          |							{
          |								"name": "y",
          |								"type": "uint256"
          |							}
          |						],
          |						"name": "c",
          |						"type": "tuple[]"
          |					}
          |				],
          |				"name": "s",
          |				"type": "tuple"
          |			},
          |			{
          |				"components": [
          |					{
          |						"name": "x",
          |						"type": "uint256"
          |					},
          |					{
          |						"name": "y",
          |						"type": "uint256"
          |					}
          |				],
          |				"name": "t",
          |				"type": "tuple"
          |			},
          |			{
          |				"name": "a",
          |				"type": "uint256"
          |			}
          |		],
          |		"name": "f",
          |		"outputs": [
          |			{
          |				"name": "",
          |				"type": "uint256"
          |			}
          |		],
          |		"payable": false,
          |		"stateMutability": "nonpayable",
          |		"type": "function"
          |	}
        """.stripMargin
      val f = abi.parseFunction(functionABI)
      f.isRight shouldBe true
      val result = f.right.get.getByteCode("[[1, [2, 3], [[4, 5], [6, 7]]], [8, 9], 10]")
      result.isRight shouldBe true
      result.right.get shouldBe hex"6f2be728000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000009000000000000000000000000000000000000000000000000000000000000000a0000000000000000000000000000000000000000000000000000000000000001000000000000000000000000000000000000000000000000000000000000006000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000000300000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000500000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000007"

      val result2 = abi.decodeOutputs(f.right.get.inputs, result.right.get.drop(4))
      result2.isRight shouldBe true

      result2.right.get shouldBe parse("[[1, [2, 3], [[4, 5], [6, 7]]], [8, 9], 10]").getOrElse(Json.Null)
    }
  }
}
