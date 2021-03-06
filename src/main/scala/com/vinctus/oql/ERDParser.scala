package com.vinctus.oql

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.input.{CharSequenceReader, Position, Positional}

object ERDParser {

  def parseDefinition(defn: String): ERDefinitionERD = {
    val p = new ERDParser

    p.parseFromString(defn, p.definition)
  }

}

class ERDParser extends RegexParsers {

  override protected val whiteSpace: Regex = """(\s|//.*)+""".r

  def pos: Parser[Position] = positioned(success(new Positional {})) ^^ {
    _.pos
  }

  def number: Parser[ExpressionERD] =
    positioned("""(?:\d+(?:\.\d+)?|\.\d+)(?:[eE][+-]?\d+)?""".r ^^ {
      case n if (n contains '.') || (n contains 'e') || (n contains 'E') =>
        FloatLiteralERD(n)
      case n => IntegerLiteralERD(n)
    })

  def string: Parser[StringLiteralERD] =
    positioned(
      (("'" ~> """[^'\n]*""".r <~ "'") |
        ("\"" ~> """[^"\n]*""".r <~ "\"")) ^^ StringLiteralERD)

  def boolean: Parser[BooleanLiteralERD] = positioned(("true" | "false") ^^ BooleanLiteralERD)

  def ident: Parser[Ident] =
    positioned("""[a-zA-Z_$][a-zA-Z0-9_$]*""".r ^^ Ident)

  def variable: Parser[VariableExpressionERD] = ident ^^ VariableExpressionERD

  def definition: Parser[ERDefinitionERD] = rep1(block) ^^ ERDefinitionERD

  def block: Parser[BlockERD] = entityBlock

  def entityBlock: Parser[EntityBlockERD] =
    "entity" ~ ident ~ opt("(" ~> ident <~ ")") ~ "{" ~ rep1(attribute) ~ "}" ^^ {
      case _ ~ n ~ a ~ _ ~ fs ~ _ =>
        EntityBlockERD(n, if (a isDefined) a.get else n, fs)
    }

  def attribute: Parser[EntityAttributeERD] =
    positioned(
      opt("*") ~ ident ~ opt("(" ~> ident <~ ")") ~ ":" ~ typeSpec ~ opt("!") ^^ {
        case pk ~ n ~ a ~ _ ~ t ~ r =>
          EntityAttributeERD(n, if (a isDefined) a.get else n, t, pk isDefined, r isDefined)
      } |
        ident ~ "=" ~ jsonLiteral ^^ {
          case n ~ _ ~ v => EntityAttributeERD(n, n, LiteralTypeERD(v), pk = false, required = true)
        })

  def jsonLiteral: Parser[ExpressionERD] = number | string | boolean | "null" ^^^ NullLiteralERD | array | jsonObject

  def array: Parser[ArrayLiteralERD] = "[" ~> repsep(jsonLiteral, ",") <~ "]" ^^ ArrayLiteralERD

  def jsonObject: Parser[ObjectLiteralERD] = "{" ~> repsep(pair, ",") <~ "}" ^^ ObjectLiteralERD

  def pair: Parser[(StringLiteralERD, ExpressionERD)] = string ~ ":" ~ jsonLiteral ^^ { case k ~ _ ~ v => k -> v }

  def typeSpec: Parser[TypeSpecifierERD] =
    ident ^^ SimpleTypeERD |
      ("[" ~> ident ~ opt("." ~> ident) <~ "]") ~ ("(" ~> ident <~ ")") ^^ {
        case e ~ a ~ j => JunctionArrayTypeERD(e, a, j)
      } |
      ("[" ~> ident ~ opt("." ~> ident) <~ "]") ^^ {
        case e ~ a =>
          ArrayTypeERD(e, a)
      } |
      ("<" ~> ident ~ opt("." ~> ident) <~ ">") ^^ {
        case e ~ a => OneToOneTypeERD(e, a)
      }

  def parseFromString[T](src: String, grammar: Parser[T]): T =
    parseAll(grammar, new CharSequenceReader(src)) match {
      case Success(tree, _)       => tree
      case NoSuccess(error, rest) => problem(rest.pos, error)
    }

}
