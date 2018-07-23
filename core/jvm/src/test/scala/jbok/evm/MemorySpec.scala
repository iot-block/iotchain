package jbok.evm

import jbok.JbokSpec
import jbok.core.models.UInt256
import jbok.testkit.VMGens._
import org.scalacheck.Arbitrary._
import org.scalacheck.Gen._
import org.scalacheck.{Arbitrary, Gen}
import scodec.bits.ByteVector

class MemorySpec extends JbokSpec {

  def zeros(size: Int): ByteVector =
    if (size <= 0)
      ByteVector.empty
    else
      ByteVector(Array.fill[Byte](size)(0))

  def consecutiveBytes(size: Int, start: Int = 0): ByteVector =
    if (size <= 0)
      ByteVector.empty
    else
      ByteVector((start until (start + size)).map(_.toByte): _*)


  def randomSizeByteArrayGen(minSize: Int, maxSize: Int): Gen[Array[Byte]] =
    Gen.choose(minSize, maxSize).flatMap(byteArrayOfNItemsGen(_))

  def byteArrayOfNItemsGen(n: Int): Gen[Array[Byte]] = Gen.listOfN(n, Arbitrary.arbitrary[Byte]).map(_.toArray)

  "memory" should {

    "Store a Byte" in {
      forAll(choose(10, 100), arbitrary[Byte], choose(0, 200)) { (initialMemorySize, b, idx) =>
        // We need this additional check.
        // Otherwise ScalaCheck generates negative numbers during shrinking.
        whenever(initialMemorySize >= 0 && idx >= 0) {
          val memory = Memory.empty.store(0, zeros(initialMemorySize)).store(idx, b)

          val expectedSize = math.max(initialMemorySize, idx + 1)
          val expectedContents = zeros(expectedSize).update(idx, b)

          memory.size shouldEqual expectedSize
          memory.load(0, memory.size)._1 shouldEqual expectedContents
        }
      }
    }

    "Store an UInt256" in {
      forAll(choose(10, 100), getUInt256Gen(), choose(0, 200)) { (initialMemorySize, uint, idx) =>
        whenever(initialMemorySize >= 0 && idx >= 0) {
          val memory = Memory.empty.store(0, zeros(initialMemorySize)).store(idx, uint)

          val expectedSize = math.max(initialMemorySize, idx + UInt256.Size)
          val expectedContents = zeros(idx) ++ uint.bytes ++ zeros(memory.size - idx - UInt256.Size)

          memory.size shouldEqual expectedSize
          memory.load(0, memory.size)._1 shouldEqual expectedContents
        }
      }
    }

    "Store an Array[Byte]" in {
      forAll(choose(10, 100), randomSizeByteArrayGen(0, 100), choose(0, 200)) { (initialMemorySize, arr, idx) =>
        whenever(initialMemorySize >= 0 && idx >= 0) {
          val memory = Memory.empty.store(0, zeros(initialMemorySize)).store(idx, arr)

          val requiredSize = if (arr.length == 0) 0 else idx + arr.length
          val expectedSize = math.max(initialMemorySize, requiredSize)
          val expectedContents =
            if (arr.length == 0)
              zeros(initialMemorySize)
            else
              zeros(idx) ++ ByteVector(arr) ++ zeros(memory.size - idx - arr.length)

          memory.size shouldEqual expectedSize
          memory.load(0, memory.size)._1 shouldEqual expectedContents
        }
      }
    }

    "Store a ByteVector" in {
      forAll(choose(10, 100), randomSizeByteArrayGen(0, 100), choose(0, 200)) { (initialMemorySize, arr, idx) =>
        whenever(initialMemorySize >= 0 && idx >= 0) {
          val bs = ByteVector(arr)
          val memory = Memory.empty.store(0, zeros(initialMemorySize)).store(idx, bs)

          val requiredSize = if (bs.isEmpty) 0 else idx + bs.length
          val expectedSize = math.max(initialMemorySize, requiredSize)
          val expectedContents =
            if (bs.isEmpty)
              zeros(initialMemorySize)
            else
              zeros(idx) ++ ByteVector(arr) ++ zeros(memory.size - idx - bs.size.toInt)

          memory.size shouldEqual expectedSize
          memory.load(0, memory.size)._1 shouldEqual expectedContents
        }
      }
    }

    "Load an UInt256" in {
      forAll(choose(0, 100), choose(0, 200)) { (initialMemorySize, idx) =>
        whenever(initialMemorySize >= 0 && idx >= 0) {
          val initialMemory = Memory.empty.store(0, consecutiveBytes(initialMemorySize))
          val (uint, memory) = initialMemory.load(idx)

          val expectedMemorySize = math.max(initialMemorySize, idx + UInt256.Size)
          val expectedContents = consecutiveBytes(initialMemorySize) ++ zeros(expectedMemorySize - initialMemorySize)
          val expectedResult = UInt256(
            if (idx >= initialMemorySize)
              zeros(UInt256.Size)
            else if (idx + UInt256.Size > initialMemorySize)
              consecutiveBytes(initialMemorySize - idx, idx) ++ zeros(idx + UInt256.Size - initialMemorySize)
            else
              consecutiveBytes(UInt256.Size, idx)
          )

          memory.size shouldEqual expectedMemorySize
          memory.load(0, memory.size)._1 shouldEqual expectedContents
          uint shouldEqual expectedResult
        }
      }
    }

    "Load a ByteVector" in {
      forAll(choose(0, 100), choose(0, 200), choose(1, 100)) { (initialMemorySize, idx, size) =>
        whenever(initialMemorySize >= 0 && idx >= 0 && size > 0) {
          val initialMemory = Memory.empty.store(0, consecutiveBytes(initialMemorySize))
          val (bs, memory) = initialMemory.load(idx, size)

          val requiredSize = if (size == 0) 0 else idx + size
          val expectedMemorySize = math.max(initialMemorySize, requiredSize)
          val expectedContents = consecutiveBytes(initialMemorySize) ++ zeros(expectedMemorySize - initialMemorySize)
          val expectedResult =
            if (idx >= initialMemorySize)
              zeros(size)
            else if (idx + size > initialMemorySize)
              consecutiveBytes(initialMemorySize - idx, idx) ++ zeros(idx + size - initialMemorySize)
            else
              consecutiveBytes(size, idx)

          memory.size shouldEqual expectedMemorySize
          memory.load(0, memory.size)._1 shouldEqual expectedContents
          bs shouldEqual expectedResult
        }
      }
    }

    "Correctly increase memory size when storing" in {

      val table = Table(
        ("initialSize", "offset", "dataSize", "expectedDelta"),
        (0, 0, 1, 1),
        (0, 0, 32, 32),
        (0, 32, 31, 63),
        (64, 32, 64, 32),
        (64, 32, 16, 0),
        (64, 96, 0, 0),
        (0, 32, 0, 0)
      )

      forAll(table) { (initialSize, offset, dataSize, expectedDelta) =>
        val initMem = Memory.empty.store(0, zeros(initialSize))
        val updatedMem = initMem.store(offset, consecutiveBytes(dataSize))
        (updatedMem.size - initMem.size) shouldEqual expectedDelta
      }

    }

    "Correctly increase memory size when loading" in {

      val table = Table(
        ("initialSize", "offset", "dataSize", "expectedDelta"),
        (0, 0, 1, 1),
        (0, 0, 32, 32),
        (0, 32, 31, 63),
        (64, 32, 64, 32),
        (64, 32, 16, 0),
        (64, 96, 0, 0),
        (0, 32, 0, 0)
      )

      forAll(table) { (initialSize, offset, dataSize, expectedDelta) =>
        val initMem = Memory.empty.store(0, zeros(initialSize))
        val updatedMem = initMem.load(offset, dataSize)._2
        (updatedMem.size - initMem.size) shouldEqual expectedDelta
      }
    }

    "Correctly increase memory size when expanding" in {

      val table = Table(
        ("initialSize", "offset", "dataSize", "expectedDelta"),
        (0, 0, 1, 1),
        (0, 0, 32, 32),
        (0, 32, 31, 63),
        (64, 32, 64, 32),
        (64, 32, 16, 0),
        (64, 96, 0, 0),
        (0, 32, 0, 0)
      )

      forAll(table) { (initialSize, offset, dataSize, expectedDelta) =>
        val initMem = Memory.empty.store(0, zeros(initialSize))
        val updatedMem = initMem.expand(offset, dataSize)
        (updatedMem.size - initMem.size) shouldEqual expectedDelta
      }
    }
  }
}
