package jbok.solidity.visitors

import jbok.solidity.visitors.ModifierList.{StateMutability, Visibility}

final case class ModifierList(modifiers: Set[String] = Set.empty,
                              visibility: Option[Visibility] = None,
                              stateMutability: Option[StateMutability] = None)

object ModifierList {
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



}
