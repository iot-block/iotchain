package jbok.sdk

import jbok.core.models._

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import scala.scalajs.js.JSConverters._
import _root_.io.circe.parser._
import _root_.io.circe.syntax._
import jbok.codec.json.implicits._

@JSExportTopLevel("JsonCodec")
@JSExportAll
object JsonCodec {
  def decodeBlockHeader(json: String): js.UndefOr[BlockHeader] =
    decode[BlockHeader](json).toOption.orUndefined

  def decodeBlockBody(json: String): js.UndefOr[BlockBody] =
    decode[BlockBody](json).toOption.orUndefined

  def decodeBlock(json: String): js.UndefOr[Block] =
    decode[Block](json).toOption.orUndefined

  def encodeTransaction(stx: Transaction): String =
    stx.asJson.noSpaces

  def decodeTransaction(json: String): js.UndefOr[Transaction] =
    decode[Transaction](json).toOption.orUndefined

  def encodeSignedTransaction(stx: SignedTransaction): String =
    stx.asJson.noSpaces

  def decodeSignedTransaction(json: String): js.UndefOr[SignedTransaction] =
    decode[SignedTransaction](json).toOption.orUndefined
}
