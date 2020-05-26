package xyz.hyperreal.oql

import scala.util.parsing.input.Positional

abstract class OQLAST

abstract class ProjectExpressionOQL extends OQLAST
case class ProjectAttributesOQL(attrs: List[ProjectExpressionOQL]) extends ProjectExpressionOQL
case object ProjectAllOQL extends ProjectExpressionOQL
case class AggregateAttributeOQL(agg: Ident, attr: Ident) extends ProjectExpressionOQL
case class QueryOQL(source: Ident,
                    project: ProjectExpressionOQL,
                    select: Option[ExpressionOQL],
                    group: Option[List[VariableExpressionOQL]],
                    order: Option[List[(ExpressionOQL, Boolean)]],
                    restrict: (Option[Int], Option[Int]))
    extends ProjectExpressionOQL

abstract class ExpressionOQL extends OQLAST with Positional
case class EqualsExpressionOQL(table: String, column: String, value: String) extends ExpressionOQL
case class VariableExpressionOQL(ids: List[Ident]) extends ExpressionOQL
case class InfixExpressionOQL(left: ExpressionOQL, op: String, right: ExpressionOQL) extends ExpressionOQL
case class PrefixExpressionOQL(op: String, expr: ExpressionOQL) extends ExpressionOQL
case class GroupedExpressionOQL(expr: ExpressionOQL) extends ExpressionOQL
case class FloatLiteralOQL(n: String) extends ExpressionOQL
case class IntegerLiteralOQL(n: String) extends ExpressionOQL
case class StringLiteralOQL(s: String) extends ExpressionOQL
