package jbok.evm

import jbok.common.{gen, CommonSpec}
import org.scalacheck.Arbitrary
import scodec.bits.ByteVector

class ProgramSpec extends CommonSpec {

  val CodeSize: Int = Byte.MaxValue
  val PositionsSize = 10

  val nonPushOp: Byte     = JUMP.code
  val invalidOpCode: Byte = 0xef.toByte

  implicit val arbPositionSet: Arbitrary[Set[Int]] =
    Arbitrary(gen.boundedList(0, PositionsSize, gen.int(0, CodeSize)).map(_.toSet))

  "program" should {

    "detect all jump destinations if there are no push op" in {
      forAll { jumpDestLocations: Set[Int] =>
        val code = ByteVector((0 to CodeSize).map { i =>
          if (jumpDestLocations.contains(i)) JUMPDEST.code
          else nonPushOp
        }.toArray)
        val program = Program(code)
        program.validJumpDestinations shouldBe jumpDestLocations
      }
    }

    "detect all jump destinations if there are push op" in {
      forAll { (jumpDestLocations: Set[Int], pushOpLocations: Set[Int]) =>
        val code = ByteVector((0 to CodeSize).map { i =>
          if (jumpDestLocations.contains(i)) JUMPDEST.code
          else if (pushOpLocations.contains(i)) PUSH1.code
          else nonPushOp
        }.toArray)
        val program = Program(code)

        //Removing the PUSH1 that would be used as a parameter of another PUSH1
        //  Example: In "PUSH1 PUSH1 JUMPDEST", the JUMPDEST is a valid jump destination
        val pushOpLocationsNotParameters = (pushOpLocations diff jumpDestLocations).toList.sorted
          .foldLeft(List.empty[Int]) {
            case (recPushOpLocations, i) =>
              if (recPushOpLocations.lastOption.contains(i - 1)) recPushOpLocations else recPushOpLocations :+ i
          }

        val jumpDestLocationsWithoutPushBefore = jumpDestLocations
          .filterNot(i => pushOpLocationsNotParameters.contains(i - 1))
          .filter(i => 0 <= i && i <= CodeSize)
        program.validJumpDestinations shouldBe jumpDestLocationsWithoutPushBefore
      }
    }

    "detect all jump destinations if there are invalid ops" in {
      forAll { (jumpDestLocations: Set[Int], invalidOpLocations: Set[Int]) =>
        val code = ByteVector((0 to CodeSize).map { i =>
          if (jumpDestLocations.contains(i)) JUMPDEST.code
          else if (invalidOpLocations.contains(i)) invalidOpCode
          else nonPushOp
        }.toArray)
        val program = Program(code)
        program.validJumpDestinations shouldBe jumpDestLocations
      }
    }

    "detect all instructions as jump destinations if they are" in {
      val code    = ByteVector((0 to CodeSize).map(_ => JUMPDEST.code).toArray)
      val program = Program(code)
      program.validJumpDestinations shouldBe (0 to CodeSize).toSet
    }
  }
}
