package com.vinctus.oql

import scala.util.parsing.input.{Position, Positional}

abstract class OQLAST

abstract class ProjectExpressionOQL extends OQLAST
case class ProjectAttributesOQL(attrs: Seq[ProjectExpressionOQL]) extends ProjectExpressionOQL
case class ProjectAllOQL(pos: Position = null) extends ProjectExpressionOQL
case class ReferenceAttributeOQL(attr: Ident) extends ProjectExpressionOQL
case class AggregateAttributeOQL(agg: List[Ident], attr: Ident) extends ProjectExpressionOQL
case class NegativeAttribute(attr: Ident) extends ProjectExpressionOQL
case class LiftedAttribute(attr: QueryOQL) extends ProjectExpressionOQL
case class QueryOQL(source: Ident,
                    project: ProjectExpressionOQL,
                    select: Option[ExpressionOQL],
                    group: Option[List[ExpressionOQL]],
                    order: Option[List[(ExpressionOQL, String)]],
                    limit: Option[Int],
                    offset: Option[Int])
    extends ProjectExpressionOQL

abstract class ExpressionOQL extends OQLAST with Positional
case class EqualsExpressionOQL(table: String, column: String, value: String) extends ExpressionOQL
case class VariableExpressionOQL(ids: List[Ident]) extends ExpressionOQL
case class ReferenceExpressionOQL(ids: List[Ident]) extends ExpressionOQL
case class ApplyExpressionOQL(func: Ident, args: List[ExpressionOQL]) extends ExpressionOQL
case class CaseExpressionOQL(whens: List[(ExpressionOQL, ExpressionOQL)], els: Option[ExpressionOQL])
    extends ExpressionOQL
case class InfixExpressionOQL(left: ExpressionOQL, op: String, right: ExpressionOQL) extends ExpressionOQL
case class PrefixExpressionOQL(op: String, expr: ExpressionOQL) extends ExpressionOQL
case class PostfixExpressionOQL(expr: ExpressionOQL, op: String) extends ExpressionOQL
case class InExpressionOQL(expr: ExpressionOQL, op: String, list: List[ExpressionOQL]) extends ExpressionOQL
case class InSubqueryExpressionOQL(expr: ExpressionOQL, op: String, query: QueryOQL) extends ExpressionOQL
case class ExistsExpressionOQL(query: QueryOQL) extends ExpressionOQL
case class SubqueryExpressionOQL(query: QueryOQL) extends ExpressionOQL
case class BetweenExpressionOQL(expr: ExpressionOQL, op: String, lower: ExpressionOQL, upper: ExpressionOQL)
    extends ExpressionOQL
case class GroupedExpressionOQL(expr: ExpressionOQL) extends ExpressionOQL
case class FloatLiteralOQL(n: String) extends ExpressionOQL
case class IntegerLiteralOQL(n: String) extends ExpressionOQL
case class StringLiteralOQL(s: String) extends ExpressionOQL
case class BooleanLiteralOQL(b: Boolean) extends ExpressionOQL
case class IntervalLiteralOQL(s: String) extends ExpressionOQL
