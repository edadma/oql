package com.vinctus.oql

import scala.util.parsing.input.Positional

case class Ident(name: String) extends Positional

abstract class ERDAST
case class ERDefinitionERD(blocks: List[BlockERD]) extends ERDAST

abstract class BlockERD extends ERDAST
case class TypeBlockERD(name: Ident, underlying: Ident, condition: ExpressionERD) extends BlockERD

abstract class ExpressionERD extends ERDAST with Positional

case class VariableExpressionERD(name: Ident) extends ExpressionERD
case class FloatLiteralERD(n: String) extends ExpressionERD
case class IntegerLiteralERD(n: String) extends ExpressionERD
case class StringLiteralERD(s: String) extends ExpressionERD
case class BooleanLiteralERD(s: String) extends ExpressionERD
case object NullLiteralERD extends ExpressionERD
case class ArrayLiteralERD(elems: List[ExpressionERD]) extends ExpressionERD
case class ObjectLiteralERD(membs: List[(StringLiteralERD, ExpressionERD)]) extends ExpressionERD
case class NotExpressionERD(expr: ExpressionERD) extends ExpressionERD
case class ComparisonExpressionERD(first: ExpressionERD, comps: List[(String, ExpressionERD)]) extends ExpressionERD
case class AndExpressionERD(left: ExpressionERD, right: ExpressionERD) extends ExpressionERD
case class OrExpressionERD(left: ExpressionERD, right: ExpressionERD) extends ExpressionERD

abstract class TypeSpecifierERD extends ERDAST with Positional
case class SimpleTypeERD(typ: Ident) extends TypeSpecifierERD
case class OneToOneTypeERD(typ: Ident, attr: Option[Ident]) extends TypeSpecifierERD
case class JunctionArrayTypeERD(typ: Ident, attr: Option[Ident], junction: Ident) extends TypeSpecifierERD
case class ArrayTypeERD(typ: Ident, attr: Option[Ident]) extends TypeSpecifierERD
case class LiteralTypeERD(value: ExpressionERD) extends TypeSpecifierERD

case class EntityBlockERD(entity: Ident, actualTable: Ident, fields: List[EntityAttributeERD]) extends BlockERD
case class EntityAttributeERD(attr: Ident, actualField: Ident, typ: TypeSpecifierERD, pk: Boolean, required: Boolean)
    extends ERDAST
