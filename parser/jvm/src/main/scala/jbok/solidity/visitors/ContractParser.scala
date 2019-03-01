package jbok.solidity.visitors

import cats.implicits._
import jbok.solidity.grammar.{SolidityLexer, SolidityParser}
import org.antlr.v4.runtime.{CharStreams, CommonTokenStream}

object ContractParser {
  def parse(input: String): Either[List[String], List[ContractDefinition]] = {

    val stream        = CharStreams.fromString(input)
    val lexer         = new SolidityLexer(stream)
    val tokens        = new CommonTokenStream(lexer)
    val parser        = new SolidityParser(tokens)
    val errorListener = new SolidityErrorListener
    parser.removeErrorListeners()
    parser.addErrorListener(errorListener)

    implicit val handler: ReportHandler = ReportHandler(parser)
    val contracts                       = SourceVisitor().visit(parser.sourceUnit())
    if (errorListener.Errors.isEmpty) {
      if (contracts.isEmpty) {
        List("cannot parse any contract in code.").asLeft
      } else {
        contracts.asRight
      }
    } else {
      errorListener.Errors.asLeft
    }
  }
}
