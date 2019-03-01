package jbok.solidity.visitors

import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}

import scala.util.Try

final case class NumberLiteralVisitor()(implicit errorHandler: ReportHandler) extends SolidityBaseVisitor[Double] {
  override def visitDecimal(ctx: SolidityParser.DecimalContext): Double = {
    super.visitDecimal(ctx)

    val decimal = Try(ctx.DecimalNumber().getText.toDouble).toOption
    if (decimal.isEmpty) {
      errorHandler.reportError(s"not a valid decimal number: ${ctx.DecimalNumber().getText}")
    }
    decimal.getOrElse(0)
  }

  override def visitHex(ctx: SolidityParser.HexContext): Double = {
    super.visitHex(ctx)

    val hexNumber = Try(java.lang.Long.decode(ctx.HexNumber().getSymbol.getText).toDouble).toOption
    if (hexNumber.isEmpty) {
      errorHandler.reportError(s"not a valid hex number: ${ctx.HexNumber().getText}")
    }
    hexNumber.getOrElse(0)
  }
}
