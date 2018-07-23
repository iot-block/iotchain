package jbok.evm

import jbok.JbokSpec
import jbok.core.models.UInt256
import jbok.testkit.VMGens
import org.scalacheck.Gen

class StackSpec extends JbokSpec {
  val maxStackSize = 32
  val stackGen = VMGens.getStackGen(maxSize = maxStackSize)
  val intGen = Gen.choose(0, maxStackSize).filter(_ >= 0)
  val uint256Gen = VMGens.getUInt256Gen()
  val uint256ListGen = VMGens.getListGen(0, 16, uint256Gen)
  
  "stack" should {
    "pop single element" in  {
      forAll(stackGen) { stack =>
        val (v, stack1) = stack.pop
        if (stack.size > 0) {
          v shouldEqual stack.toSeq.head
          stack1.toSeq shouldEqual stack.toSeq.tail
        } else {
          v shouldEqual 0
          stack1 shouldEqual stack
        }
      }
    }

    "pop multiple elements" in  {
      forAll(stackGen, intGen) { (stack, i) =>
        val (vs, stack1) = stack.pop(i)
        if (stack.size >= i) {
          vs shouldEqual stack.toSeq.take(i)
          stack1.toSeq shouldEqual stack.toSeq.drop(i)
        } else {
          vs shouldEqual Seq.fill(i)(UInt256.Zero)
          stack1 shouldEqual stack
        }
      }
    }

    "push single element" in  {
      forAll(stackGen, uint256Gen) { (stack, v) =>
        val stack1 = stack.push(v)

        if (stack.size < stack.maxSize) {
          stack1.toSeq shouldEqual (v +: stack.toSeq)
        } else {
          stack1 shouldEqual stack
        }
      }
    }

    "push multiple elements" in  {
      forAll(stackGen, uint256ListGen) { (stack, vs) =>
        val stack1 = stack.push(vs)

        if (stack.size + vs.size <= stack.maxSize) {
          stack1.toSeq shouldEqual (vs.reverse ++ stack.toSeq)
        } else {
          stack1 shouldEqual stack
        }
      }
    }

    "duplicate element" in  {
      forAll(stackGen, intGen) { (stack, i) =>
        val stack1 = stack.dup(i)

        if (i < stack.size && stack.size < stack.maxSize) {
          val x = stack.toSeq(i)
          stack1.toSeq shouldEqual (x +: stack.toSeq)
        } else {
          stack1 shouldEqual stack
        }
      }
    }

    "swap elements" in  {
      forAll(stackGen, intGen) { (stack, i) =>
        val stack1 = stack.swap(i)

        if (i < stack.size) {
          val x = stack.toSeq.head
          val y = stack.toSeq(i)
          stack1.toSeq shouldEqual stack.toSeq.updated(0, y).updated(i, x)
        } else {
          stack1 shouldEqual stack
        }
      }
    }
  }
}
