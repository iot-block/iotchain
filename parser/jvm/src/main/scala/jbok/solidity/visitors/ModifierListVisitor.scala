package jbok.solidity.visitors

import cats.implicits._
import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}
import jbok.solidity.visitors.ModifierList._

import scala.collection.JavaConverters._

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

    def merge(left: ModifierList, right: ModifierList): ModifierList = {
      val newModifiers = if ((left.modifiers & right.modifiers).isEmpty) {
        left.modifiers | right.modifiers
      } else {
        reportHandler.reportError(s"modifier already defined: ${left.modifiers & right.modifiers}")
        left.modifiers | right.modifiers
      }

      val newVisibility = if (left.visibility.isDefined && right.visibility.isDefined) {
        reportHandler.reportError(s"visibility double defined: ${left.visibility} ${right.visibility}")
        left.visibility orElse right.visibility
      } else {
        left.visibility orElse right.visibility
      }

      val newStateMutability = if (left.stateMutability.isDefined && right.stateMutability.isDefined) {
        reportHandler.reportError(s"mutability double defined: ${left.stateMutability} ${right.stateMutability}")
        left.stateMutability orElse right.stateMutability
      } else {
        left.stateMutability orElse right.stateMutability
      }

      ModifierList(newModifiers, newVisibility, newStateMutability)
    }

    modifiers.fold(ModifierList()) { (mm, acc) =>
      merge(mm, acc)
    }
  }
}
