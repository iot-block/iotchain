package jbok.sdk

import cats.effect.IO
import jbok.common.execution._
import scodec.bits.ByteVector

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.Promise
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.typedarray.{Int8Array, byteArray2Int8Array}

@JSExportTopLevel("Option")
@JSExportAll
object JSOption {
  def None(): scala.None.type = scala.None

  def Some[A](value: A): scala.Some[A] = scala.Some(value)

  def UndefOr[A](opt: Option[A]): js.UndefOr[A] = opt.orUndefined
}

@JSExportTopLevel("BigInt")
@JSExportAll
object JSBigInt {
  def fromString(num: String): BigInt = BigInt(num)

  def toString(bi: BigInt): String = bi.toString(10)
}

object JSPromise {
  @JSExportTopLevel("Call")
  def call[A](f: Future[A]): Promise[A] = f.toJSPromise

  @JSExportTopLevel("Call")
  def call[A](f: IO[A]): Promise[A] = f.unsafeToFuture().toJSPromise
}

@JSExportTopLevel("ByteVector")
@JSExportAll
object JSByteVector {
  def toString(bv: ByteVector): String = bv.toHex

  def toArray(bv: ByteVector): Int8Array = byteArray2Int8Array(bv.toArray)

  def fromString(data: String): ByteVector = ByteVector.fromValidHex(data)

  def fromArray(ab: js.Array[Byte]): ByteVector = ByteVector(ab.toArray)
}
