package jbok.solidity.visitors

import jbok.solidity.grammar.{SolidityBaseVisitor, SolidityParser}
import jbok.solidity.visitors.ContractPart._
import org.antlr.v4.runtime.Token

import scala.collection.JavaConverters._

final case class SourceVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[List[ContractDefinition]] {
  override def visitSourceUnit(ctx: SolidityParser.SourceUnitContext): List[ContractDefinition] = {
    super.visitSourceUnit(ctx)

    ctx
      .contractDefinition()
      .asScala
      .toList
      .foldLeft(List.empty[ContractDefinition])((contracts, ctx) =>
        ctx.accept(ContractVisitor()) match {
          case cr @ ContractDefinition("contract", _, _, _, _, _) =>
            if (contracts.map(_.id).toSet.contains(cr.id)) {
              reportHandler.reportError(s"identifier ${cr.id} already declared.", ctx.start)
            }
            contracts :+ cr
          case _ => contracts
      })

  }
}

final case class ContractVisitor()(implicit reportHandler: ReportHandler)
    extends SolidityBaseVisitor[ContractDefinition] {
  override def visitContractDefinition(ctx: SolidityParser.ContractDefinitionContext): ContractDefinition = {
    super.visitContractDefinition(ctx)
    ctx.start.getText match {
      case "contract" | "library" | "interface" =>
        val identity = ctx.identifier().accept(IdentityVisitor())
        if (identity.isEmpty) {
          reportHandler.reportError("contract name cannot be empty.", ctx.identifier().getStart)
        }

        def check(contract: ContractDefinition, identifier: String, token: Token): Unit =
          if (contract.functions.contains(identifier) || contract.stateVariable.contains(identifier) || contract.structDefinitions
                .contains(identifier) || contract.enumDefinitions.contains(identifier)) {
            reportHandler.reportError(s"identifier $identifier already declared.", token)
          }

        val contract = ContractDefinition.empty.copy(`type` = ctx.start.getText, id = identity.getOrElse(""))
        ctx.contractPart().asScala.toList.foldLeft(contract) {
          case (cd, subCtx) =>
            subCtx.accept(ContractPartVisitor()) match {
              case func: FunctionDefinition =>
                val f = if (func.name == cd.id) func.copy(name = "constructor") else func

                check(cd, f.name, subCtx.start)
                cd.copy(functions = cd.functions + (f.name -> f))
              case variable: StateVariableDefinition =>
                check(cd, variable.id, subCtx.start)

                cd.copy(stateVariable = cd.stateVariable + (variable.id -> variable))
              case struct: StructDefinition =>
                check(cd, struct.id, subCtx.start)

                cd.copy(structDefinitions = cd.structDefinitions + (struct.id -> struct))
              case enum: EnumDefinition =>
                check(cd, enum.id, subCtx.start)

                cd.copy(enumDefinitions = cd.enumDefinitions + (enum.id -> enum))
              case _ => cd
            }
        }
      case _ =>
        reportHandler.reportError(s"${ctx.start} unknown contract start token.", ctx.start)
        ContractDefinition.empty.copy(`type` = "unknown")
    }
  }
}
