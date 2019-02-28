package jbok.solidity.visitors

import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}

final case class ConstantVisitor()(implicit reportHandler: ReportHandler) extends SolidityBaseVisitor[Double] {
  override def visitBinaryOp(ctx: SolidityParser.BinaryOpContext): Double = {
    super.visitBinaryOp(ctx)

    val left  = visit(ctx.constantExpression(0))
    val right = visit(ctx.constantExpression(1))
    ctx.op.getText match {
      case "**" => scala.math.pow(left, right).toInt
      case "*"  => left * right
      case "/"  => left / right // if get fractional value, throw error ?
      case "%"  => left % right
      case "+"  => left + right
      case "-"  => left - right
      case _ =>
        if (!isInt(left)) {
          reportHandler.reportError(s"${ctx.op.getText} left $left expression shouldBe Int",
                                    ctx.constantExpression(0).start)
        }

        if (!isInt(right)) {
          reportHandler.reportError(s"${ctx.op.getText} right $right expression shouldBe Int",
                                    ctx.constantExpression(1).start)
        }

        if (ctx.op.getText == ">>") {
          left.toInt >> right.toInt
        } else {
          left.toInt << right.toInt
        }
    }

  }

  override def visitParens(ctx: SolidityParser.ParensContext): Double = {
    super.visitParens(ctx)

    visit(ctx.constantExpression())
  }

  override def visitUnaryOp(ctx: SolidityParser.UnaryOpContext): Double = {
    super.visitUnaryOp(ctx)

    val expr = visit(ctx.constantExpression())
    if (ctx.op.getText == "+") {
      expr
    } else {
      -expr
    }
  }

  override def visitNumber(ctx: SolidityParser.NumberContext): Double = {
    super.visitNumber(ctx)

    NumberLiteralVisitor().visit(ctx.numberLiteralNoUnit())
  }

  def isInt(n: Double): Boolean = if (n % 1 == 0) true else false
}
