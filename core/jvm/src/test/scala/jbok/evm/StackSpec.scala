package jbok.evm

import jbok.common.CommonSpec
import jbok.core.models.UInt256
import jbok.core.testkit._
import jbok.evm.testkit._
import org.scalacheck.Gen

class StackSpec extends CommonSpec {
  val maxStackSize   = 32
  val stackGen       = arbStack(maxStackSize, uint256Gen()).arbitrary
  val intGen         = Gen.choose(0, maxStackSize).filter(_ >= 0)
  val uint256ListGen = getListGen(0, 16, uint256Gen())

  "Stack" should {
    "pop single element" in {
      forAll(stackGen) { stack =>
        val (v, stack1) = stack.pop
        if (stack.size > 0) {
          v shouldEqual stack.toList.head
          stack1.toList shouldEqual stack.toList.tail
        } else {
          v shouldEqual 0
          stack1 shouldEqual stack
        }
      }
    }

    "pop multiple elements" in {
      forAll(stackGen, intGen) { (stack, i) =>
        val (vs, stack1) = stack.pop(i)
        if (stack.size >= i) {
          vs shouldEqual stack.toList.take(i)
          stack1.toList shouldEqual stack.toList.drop(i)
        } else {
          vs shouldEqual Seq.fill(i)(UInt256.Zero)
          stack1 shouldEqual stack
        }
      }
    }

    "push single element" in {
      forAll(stackGen, uint256Gen()) { (stack, v) =>
        val stack1 = stack.push(v)

        if (stack.size < stack.maxSize) {
          stack1.toList shouldEqual (v +: stack.toList)
        } else {
          stack1 shouldEqual stack
        }
      }
    }

    "push multiple elements" in {
      forAll(stackGen, uint256ListGen) { (stack, vs) =>
        val stack1 = stack.push(vs)

        if (stack.size + vs.size <= stack.maxSize) {
          stack1.toList shouldEqual (vs.reverse ++ stack.toList)
        } else {
          stack1 shouldEqual stack
        }
      }
    }

    "duplicate element" in {
      forAll(stackGen, intGen) { (stack, i) =>
        val stack1 = stack.dup(i)

        if (i < stack.size && stack.size < stack.maxSize) {
          val x = stack.toList(i)
          stack1.toList shouldEqual (x +: stack.toList)
        } else {
          stack1 shouldEqual stack
        }
      }
    }

    "swap elements" in {
      forAll(stackGen, intGen) { (stack, i) =>
        val stack1 = stack.swap(i)

        if (i < stack.size) {
          val x = stack.toList.head
          val y = stack.toList(i)
          stack1.toList shouldEqual stack.toList.updated(0, y).updated(i, x)
        } else {
          stack1 shouldEqual stack
        }
      }
    }
  }
}
