package jbok.crypto.codec

import java.nio.charset.StandardCharsets

import scodec.Encoder
import scodec.bits.{BitVector, ByteVector}

trait CodecSyntax {
  implicit final def stringSyntax(a: String): StringOps = new StringOps(a)
  implicit final def byteArraySyntax(a: Array[Byte]): ByteArrayOps = new ByteArrayOps(a)
  implicit final def codecSyntax[A](a: A): CodecOps[A] = new CodecOps[A](a)
}

final class StringOps(val a: String) extends AnyVal {
  def utf8bytes: ByteVector = ByteVector(a.getBytes(StandardCharsets.UTF_8))
}

final class ByteArrayOps(val a: Array[Byte]) extends AnyVal {
  def bv: ByteVector = ByteVector(a)
}

final class CodecOps[A](val a: A) extends AnyVal {
  def bits(implicit encoder: Encoder[A]): BitVector = encoder.encode(a).require

  def bytes(implicit encoder: Encoder[A]): ByteVector = bits.toByteVector
}
