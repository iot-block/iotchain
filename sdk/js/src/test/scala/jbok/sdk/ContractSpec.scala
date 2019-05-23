//package jbok.sdk
//
//import cats.effect.IO
//import jbok.JbokAsyncSpec
//
//import scala.concurrent.ExecutionContext
//
//class ContractSpec extends JbokAsyncSpec {
//
//  "Contract Parser" should {
//    "parse contract code" in {
//      val code =
//        """pragma solidity >=0.4.0 <0.6.0;
//          |pragma experimental ABIEncoderV2;
//          |
//          |contract Vaccine {
//          |    address public minter;
//          |
//          |    uint256 private recoderAmount;
//          |
//          |    mapping (string => string) private values;
//          |
//          |    constructor() public {
//          |        minter = msg.sender;
//          |    }
//          |
//          |    function setValue(string memory key, string memory newValue) public onlyOwner {
//          |        require(msg.sender == minter,"没有权限");
//          |        require(bytes(key).length != 0,"invalid key");
//          |        require(bytes(newValue).length != 0,"invalid value");
//          |
//          |        if(bytes(values[key]).length==0){
//          |            recoderAmount++;
//          |        }
//          |
//          |        values[key] = newValue;
//          |    }
//          |
//          |    function batchSetValues(string[] memory keys,string[] memory newValues) public onlyOwner {
//          |
//          |        require(keys.length == newValues.length,"invalid keys and values");
//          |
//          |        for (uint i = 0;i<keys.length;i++) {
//          |
//          |            require(bytes(keys[i]).length != 0,"invalid key");
//          |            require(bytes(newValues[i]).length != 0,"invalid value");
//          |
//          |            if(bytes(values[keys[i]]).length==0){
//          |            recoderAmount++;
//          |        }
//          |
//          |            values[keys[i]] = newValues[i];
//          |        }
//          |    }
//          |
//          |    function getValue(string memory key) public onlyOwner view returns (string memory){
//          |
//          |        return values[key];
//          |    }
//          |
//          |    function batchGetValues(string[] memory keys) public onlyOwner view returns (string[] memory){
//          |
//          |        string[] memory list = new string[](keys.length);
//          |        for (uint i = 0;i<keys.length;i++) {
//          |            list[i] = values[keys[i]];
//          |        }
//          |        return list;
//          |    }
//          |
//          |    function getRecoderAmount() public view onlyOwner returns(uint256 ){
//          |
//          |        return recoderAmount;
//          |    }
//          |
//          |    modifier onlyOwner {
//          |        require(msg.sender == minter,"No Permission");
//          |        _;
//          |    }
//          |}
//        """.stripMargin
//      val parsedResult = ContractParser.parse(code)
//      val contracts    = io.circe.parser.parse(parsedResult).right.get.as[ParsedResult].right.get.contracts.get
//      val setValuePayload =
//        contracts.encode("Vaccine", "batchSetValues", "[[\"key2\", \"key3\"], [\"value2\", \"value3\"]]")
//
//      setValuePayload.toOption shouldBe Some(
//        "d885851e0000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000012000000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000008000000000000000000000000000000000000000000000000000000000000000046b6579320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000046b65793300000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000000000000676616c7565320000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000676616c7565330000000000000000000000000000000000000000000000000000")
//    }
//  }
//}
