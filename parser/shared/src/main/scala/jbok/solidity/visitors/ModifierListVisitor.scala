package jbok.solidity.visitors

import cats.implicits._
import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}

import scala.collection.JavaConverters._

final case class Modifier()

sealed trait Visibility

object Public extends Visibility

object Private extends Visibility

object Internal extends Visibility

object External extends Visibility

sealed trait StateMutability

object Pure extends StateMutability

object Constant extends StateMutability

object Payable extends StateMutability

object View extends StateMutability

final case class ModifierList(modifiers: Set[String] = Set.empty,
                              visibility: Option[Visibility] = None,
                              stateMutability: Option[StateMutability] = None) {
  def merge(modifierList: ModifierList)(implicit reportHandler: ReportHandler): ModifierList = {
    val newModifiers = if ((modifiers & modifierList.modifiers).isEmpty) {
      modifiers | modifierList.modifiers
    } else {
      reportHandler.reportError(s"modifier already defined: ${modifiers & modifierList.modifiers}")
      modifiers | modifierList.modifiers
    }

    val newVisibility = if (visibility.isDefined && modifierList.visibility.isDefined) {
      reportHandler.reportError(s"visibility double defined: ${visibility} ${modifierList.visibility}")
      visibility orElse modifierList.visibility
    } else {
      visibility orElse modifierList.visibility
    }

    val newStateMutability = if (stateMutability.isDefined && modifierList.stateMutability.isDefined) {
      reportHandler.reportError(s"mutability double defined: ${stateMutability} ${modifierList.stateMutability}")
      stateMutability orElse modifierList.stateMutability
    } else {
      stateMutability orElse modifierList.stateMutability
    }

    ModifierList(newModifiers, newVisibility, newStateMutability)
  }
}

final case class ModifierListVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[ModifierList] {
  override def visitStateMutability(ctx: SolidityParser.StateMutabilityContext): ModifierList = {
    super.visitStateMutability(ctx)

    ctx.start.getText match {
      case "pure"    => ModifierList(stateMutability = Pure.some)
      case "payalbe" => ModifierList(stateMutability = Payable.some)
      case "view"    => ModifierList(stateMutability = View.some)
      case _         => ModifierList(stateMutability = Constant.some)
    }
  }

  override def visitVisibility(ctx: SolidityParser.VisibilityContext): ModifierList = {
    super.visitVisibility(ctx)

    ctx.start.getText match {
      case "internal" => ModifierList(visibility = Internal.some)
      case "external" => ModifierList(visibility = External.some)
      case "private"  => ModifierList(visibility = Private.some)
      case _          => ModifierList(visibility = Public.some)
    }
  }

  override def visitModifierInvocation(ctx: SolidityParser.ModifierInvocationContext): ModifierList = {
    super.visitModifierInvocation(ctx)

    val identifier = ctx.identifier().accept(IdentityVisitor()).getOrElse("")

    ModifierList(modifiers = Set(identifier))
  }

  override def visitModifierList(ctx: SolidityParser.ModifierListContext): ModifierList = {
    super.visitModifierList(ctx)

    val modifiers = ctx.modifierElement().asScala.toList.map(visit(_))

    modifiers.fold(ModifierList()) { (mm, acc) =>
      mm merge acc
    }
  }
}
