package jbok.evm

import jbok.common.gen
import jbok.core.{CoreSpec, StatelessGen}
import jbok.core.models.UInt256
import org.scalacheck.{Arbitrary, Gen}

class StackSpec extends CoreSpec {
  val maxStackSize            = 32
  implicit val arbStack       = Arbitrary(StatelessGen.stack(maxStackSize, StatelessGen.uint256()))
  implicit val arbInt         = Arbitrary(Gen.choose(0, maxStackSize))
  implicit val arbUint256List = Arbitrary(gen.boundedList(0, 16, StatelessGen.uint256()))

  "Stack" should {
    "pop single element" in {
      forAll { stack: Stack =>
        val (v, stack1) = stack.pop
        if (stack.size > 0) {
          v shouldBe stack.toList.head
          stack1.toList shouldBe stack.toList.tail
        } else {
          v shouldBe 0
          stack1 shouldBe stack
        }
      }
    }

    "pop multiple elements" in {
      forAll { (stack: Stack, i: Int) =>
        val (vs, stack1) = stack.pop(i)
        if (stack.size >= i) {
          vs shouldBe stack.toList.take(i)
          stack1.toList shouldBe stack.toList.drop(i)
        } else {
          vs shouldBe Seq.fill(i)(UInt256.zero)
          stack1 shouldBe stack
        }
      }
    }

    "push single element" in {
      forAll { (stack: Stack, v: UInt256) =>
        val stack1 = stack.push(v)

        if (stack.size < stack.maxSize) {
          stack1.toList shouldBe (v +: stack.toList)
        } else {
          stack1 shouldBe stack
        }
      }
    }

    "push multiple elements" in {
      forAll { (stack: Stack, vs: List[UInt256]) =>
        val stack1 = stack.push(vs)

        if (stack.size + vs.size <= stack.maxSize) {
          stack1.toList shouldBe (vs.reverse ++ stack.toList)
        } else {
          stack1 shouldBe stack
        }
      }
    }

    "duplicate element" in {
      forAll { (stack: Stack, i: Int) =>
        val stack1 = stack.dup(i)

        if (i < stack.size && stack.size < stack.maxSize) {
          val x = stack.toList(i)
          stack1.toList shouldBe (x +: stack.toList)
        } else {
          stack1 shouldBe stack
        }
      }
    }

    "swap elements" in {
      forAll { (stack: Stack, i: Int) =>
        val stack1 = stack.swap(i)

        if (i < stack.size) {
          val x = stack.toList.head
          val y = stack.toList(i)
          stack1.toList shouldBe stack.toList.updated(0, y).updated(i, x)
        } else {
          stack1 shouldBe stack
        }
      }
    }
  }
}
