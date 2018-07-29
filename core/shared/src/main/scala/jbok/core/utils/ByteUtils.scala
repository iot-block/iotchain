package jbok.core.utils

import java.nio.{ByteBuffer, ByteOrder}

import scodec.bits.ByteVector

object ByteUtils {
  def or(arrays: ByteVector*): ByteVector = {
    require(arrays.map(_.length).distinct.length <= 1, "All the arrays should have the same length")
    require(arrays.nonEmpty, "There should be one or more arrays")

    val zeroes = ByteVector.fill(arrays.head.length)(0.toByte)
    arrays.foldLeft[ByteVector](zeroes) {
      case (acc, cur) => acc or cur
    }
  }

  def and(arrays: ByteVector*): ByteVector = {
    require(arrays.map(_.length).distinct.length <= 1, "All the arrays should have the same length")
    require(arrays.nonEmpty, "There should be one or more arrays")

    val ones = ByteVector.fill(arrays.head.length)(0xFF.toByte)
    arrays.foldLeft[ByteVector](ones) {
      case (acc, cur) => acc and cur
    }
  }

  def bytesToInts(bytes: Array[Byte]): Array[Int] =
    bytes.grouped(4).map(getIntFromWord).toArray

  def intsToBytes(input: Array[Int]): Array[Byte] = {
    input.flatMap { i =>
      Array(
        (i & 0xFF).toByte,
        ((i >> 8) & 0xFF).toByte,
        ((i >> 16) & 0xFF).toByte,
        ((i >> 24) & 0xFF).toByte
      )
    }
  }

  def getIntFromWord(arr: Array[Byte]): Int = {
    ByteBuffer.wrap(arr, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt
  }
}
