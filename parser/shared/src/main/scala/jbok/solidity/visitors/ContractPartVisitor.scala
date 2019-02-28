package jbok.solidity.visitors

import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}
import cats.implicits._

import scala.collection.JavaConverters._

final case class ContractPartVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[ContractPart] {
  override def visitFunctionPart(ctx: SolidityParser.FunctionPartContext): ContractPart = {
    super.visitFunctionPart(ctx)

    FunctionVisitor().visit(ctx.functionDefinition())
  }

  override def visitStateVariablePart(ctx: SolidityParser.StateVariablePartContext): ContractPart = {
    super.visitStateVariablePart(ctx)

    StateVariableVisitor().visit(ctx.stateVariableDeclaration())
  }

  override def visitStructPart(ctx: SolidityParser.StructPartContext): ContractPart = {
    super.visitStructPart(ctx)

    StructDefinitionVisitor().visit(ctx.structDefinition())
  }

  override def visitUsingPart(ctx: SolidityParser.UsingPartContext): ContractPart = {
    super.visitUsingPart(ctx)

    NoImplementation()
  }

  override def visitConstructorPart(ctx: SolidityParser.ConstructorPartContext): ContractPart = {
    super.visitConstructorPart(ctx)

    ConstructorVisitor().visit(ctx.constructorDefinition())
  }

  override def visitModifierPart(ctx: SolidityParser.ModifierPartContext): ContractPart = {
    super.visitModifierPart(ctx)

    NoImplementation()
  }

  override def visitEventPart(ctx: SolidityParser.EventPartContext): ContractPart = {
    super.visitEventPart(ctx)

    NoImplementation()
  }

  override def visitEnumPart(ctx: SolidityParser.EnumPartContext): ContractPart = {
    super.visitEnumPart(ctx)

    EnumDefinitionVisitor().visit(ctx.enumDefinition())
  }
}

final case class FunctionVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[FunctionDefinition] {
  override def visitFunctionDefinition(ctx: SolidityParser.FunctionDefinitionContext): FunctionDefinition = {
    super.visitFunctionDefinition(ctx)

    val identity = if (ctx.identifier() != null) ctx.identifier().accept(IdentityVisitor()) else None

    val paramList = ctx.parameterList().accept(ParameterListVisitor())
    val returnParams =
      if (ctx.returnParameters() != null) ctx.returnParameters().accept(ParameterListVisitor())
      else List.empty[ParameterDefinition]
    val modifiers =
      if (ctx.modifierList() != null) ctx.modifierList().accept(ModifierListVisitor()) else ModifierList()

    FunctionDefinition(identity.getOrElse(""), paramList, returnParams, modifiers)
  }
}

final case class ConstructorVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[FunctionDefinition] {
  override def visitConstructorDefinition(ctx: SolidityParser.ConstructorDefinitionContext): FunctionDefinition = {
    super.visitConstructorDefinition(ctx)

    val modifiers = if (ctx.modifierList() != null) ModifierListVisitor().visit(ctx.modifierList()) else ModifierList()

    val paramList = ctx.parameterList().accept(ParameterListVisitor())
    FunctionDefinition("constructor", paramList, List.empty, modifiers)
  }
}

final case class StateVariableVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[StateVariableDefinition] {
  override def visitStateVariableDeclaration(
      ctx: SolidityParser.StateVariableDeclarationContext): StateVariableDefinition = {
    super.visitStateVariableDeclaration(ctx)

    val identity = if (ctx.identifier() != null) ctx.identifier().accept(IdentityVisitor()) else None

    val visibility =
      if (ctx.variableModifiers().PublicKeyword() != null) Public.some
      else if (ctx.variableModifiers().PrivateKeyword() != null) Private.some
      else if (ctx.variableModifiers().InternalKeyword() != null) Internal.some
      else None
    val isConstant = if (ctx.variableModifiers().ConstantKeyword() != null) true else false

    val pt = ParameterTypeVisitor().visit(ctx.typeNameParam())
    pt match {
      case MappingType(inputs, outputs) =>
        StateVariableDefinition(identity.getOrElse(""), inputs, outputs, visibility, isConstant)
      case pt @ ParameterType(_, _) =>
        StateVariableDefinition(identity.getOrElse(""), Nil, List(pt), visibility, isConstant)
    }
  }

}

final case class EnumDefinitionVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[EnumDefinition] {
  override def visitEnumDefinition(ctx: SolidityParser.EnumDefinitionContext): EnumDefinition = {
    super.visitEnumDefinition(ctx)

    val identityOpt = if (ctx.identifier() != null) ctx.identifier().accept(IdentityVisitor()) else None
    if (identityOpt.isEmpty) {
      reportHandler.reportError("struct name cannot be empty.", ctx.identifier().getStart)
    }

    val ids = ctx.enumValue().asScala.toList.foldLeft(Set.empty[String]) {
      case (ids, ctx) =>
        val id = ctx.identifier().accept(IdentityVisitor()).getOrElse("")
        if (ids.contains(id)) {
          reportHandler.reportError(s"double defined identity in enum ${id}", ctx.start)
        }
        ids + id
    }

    EnumDefinition(identityOpt.getOrElse(""), ids)
  }
}

final case class StructDefinitionVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[StructDefinition] {
  override def visitStructDefinition(ctx: SolidityParser.StructDefinitionContext): StructDefinition = {
    super.visitStructDefinition(ctx)

    val identityOpt = if (ctx.identifier() != null) ctx.identifier().accept(IdentityVisitor()) else None
    if (identityOpt.isEmpty) {
      reportHandler.reportError("struct name cannot be empty.", ctx.identifier().getStart)
    }
    val variables = ctx
      .variableDeclaration()
      .asScala
      .toList
      .map(_.accept(VariableDeclaration()))

    StructDefinition(identityOpt.getOrElse(""), variables)
  }
}

final case class VariableDeclaration()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[ParameterDefinition] {
  override def visitVariableDeclaration(ctx: SolidityParser.VariableDeclarationContext): ParameterDefinition = {
    super.visitVariableDeclaration(ctx)

    val identity = if (ctx.identifier() != null) ctx.identifier().accept(IdentityVisitor()) else None
    if (identity.isEmpty) {
      reportHandler.reportError("variable declaration must have name", ctx.identifier().start)
    }
    val typeNameParam = ctx.typeNameParam().accept(ParameterTypeVisitor())
    ParameterDefinition(identity, typeNameParam)
  }
}

final case class IdentityVisitor()(implicit reportHandler: ReportHandler) extends SolidityBaseVisitor[Option[String]] {
  override def visitIdentifier(ctx: SolidityParser.IdentifierContext): Option[String] =
    scala.util.Try(ctx.Identifier().getText).toOption
}
