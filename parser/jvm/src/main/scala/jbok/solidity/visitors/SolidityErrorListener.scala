package jbok.solidity.visitors

import jbok.solidity.grammar.SolidityParser
import org.antlr.v4.runtime.{BaseErrorListener, RecognitionException, Recognizer, Token}

import scala.collection.mutable.ListBuffer

final case class ReportHandler(parser: SolidityParser) {
  def reportError(msg: String, token: Token): Unit = parser.notifyErrorListeners(token, msg, null)

  def reportError(msg: String): Unit = parser.notifyErrorListeners(msg)
}

final class SolidityErrorListener() extends BaseErrorListener {
  def Errors: List[String] = errors.toList

  private val errors: ListBuffer[String] = ListBuffer.empty[String]

  override def syntaxError(recognizer: Recognizer[_, _],
                           offendingSymbol: scala.Any,
                           line: Int,
                           charPositionInLine: Int,
                           msg: String,
                           e: RecognitionException): Unit = {
    super.syntaxError(recognizer, offendingSymbol, line, charPositionInLine, msg, e)

    val error = s"line $line: $charPositionInLine $msg"
    errors += error
  }
}
