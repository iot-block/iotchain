package jbok.solidity.visitors

import cats.implicits._
import jbok.solidity.{InvalidSolidityType, SolidityType}
import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}
import jbok.solidity.visitors.TypeNameParameter._

import scala.collection.JavaConverters._
import scala.util.Try

final case class ParameterTypeVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[TypeNameParameter] {

  override def visitPrimaryType(ctx: SolidityParser.PrimaryTypeContext): TypeNameParameter = {
    super.visitPrimaryType(ctx)

    val solidityType = SolidityType.getType(ctx.elementaryTypeName().getText)
    solidityType.left.foreach(e => reportHandler.reportError(e.toString, ctx.elementaryTypeName().start))

    ParameterType(solidityType.right.getOrElse(InvalidSolidityType()), Nil)
  }

  override def visitArrayType(ctx: SolidityParser.ArrayTypeContext): TypeNameParameter = {
    super.visitArrayType(ctx)

    val constantExpressionVisitor = ConstantVisitor()
    val pt                        = visit(ctx.typeNameParam).asInstanceOf[ParameterType]
    val constantSize =
      if (ctx.constantExpression() != null) {
        val size = ctx.constantExpression().accept(constantExpressionVisitor)
        if (constantExpressionVisitor.isInt(size) && size > 0) size.toInt.some
        else {
          reportHandler.reportError(s"array size(${size}) must be above zero Int.", ctx.constantExpression().start)
          None
        }
      } else Some(0)
    val arrayList = constantSize.map(pt.arrayList :+ _).getOrElse(pt.arrayList)
    ParameterType(pt.solidityType, arrayList)
  }

  override def visitUserDefinedType(ctx: SolidityParser.UserDefinedTypeContext): TypeNameParameter = {
    super.visitUserDefinedType(ctx)

    Undone
  }

  override def visitMappingType(ctx: SolidityParser.MappingTypeContext): TypeNameParameter = {
    super.visitMappingType(ctx)

    val keyTypeParameter   = ElementaryTypeNameVisitor().visit(ctx.mapping().elementaryTypeName())
    val valueTypeParameter = visit(ctx.mapping().typeNameParam())
    val ktp                = keyTypeParameter.asInstanceOf[ParameterType]
    valueTypeParameter match {
      case MappingType(inputs, outputs) => MappingType(List(ktp) ++ inputs, outputs)
      case pt @ ParameterType(_, _)     => MappingType(List(ktp), List(pt))
    }
  }
}

final case class ElementaryTypeNameVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[TypeNameParameter] {
  override def visitElementaryTypeName(ctx: SolidityParser.ElementaryTypeNameContext): TypeNameParameter = {
    super.visitElementaryTypeName(ctx)

    val solidityType = SolidityType.getType(ctx.getText)
    solidityType.left.foreach(e => reportHandler.reportError(e.toString, ctx.start))

    ParameterType(solidityType.right.getOrElse(InvalidSolidityType()), Nil)
  }
}

final case class ParameterListVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[List[ParameterDefinition]] {

  override def visitParameterList(ctx: SolidityParser.ParameterListContext): List[ParameterDefinition] = {
    super.visitParameterList(ctx)

    ctx.parameterStatement().asScala.toList.map(_.accept(ParameterStatementVistor()))
  }

  override def visitReturnParameters(ctx: SolidityParser.ReturnParametersContext): List[ParameterDefinition] = {
    super.visitReturnParameters(ctx)

    ctx.parameterList().accept(this)
  }
}

final case class ParameterStatementVistor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[ParameterDefinition] {
  override def visitParameterStatement(ctx: SolidityParser.ParameterStatementContext): ParameterDefinition = {
    super.visitParameterStatement(ctx)

    val identity        = if (ctx.identifier() != null) ctx.identifier().accept(IdentityVisitor()) else None
    val storageLocation = Try(ctx.storageLocation().accept(this)).toOption
    val typeNameParam   = ctx.typeNameParam().accept(ParameterTypeVisitor())
    ParameterDefinition(identity, typeNameParam)
  }
}
