package com.vinctus.oql

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.input.{CharSequenceReader, Position, Positional}

object OQLParser {

  def parseQuery(query: String): QueryOQL = {
    val p = new OQLParser

    p.parseFromString(query, p.query)
  }

  def parseSelect(s: String): ExpressionOQL = {
    val p = new OQLParser

    p.parseFromString(s, p.logicalExpression)
  }

  def parseGroup(s: String): Seq[VariableExpressionOQL] = {
    val p = new OQLParser

    p.parseFromString(s, p.variables)
  }

  def parseOrder(s: String): Seq[(ExpressionOQL, Boolean)] = {
    val p = new OQLParser

    p.parseFromString(s, p.orderExpressions)
  }

}

class OQLParser extends RegexParsers {

  override protected val whiteSpace: Regex = """(\s|;.*)+""".r

  def pos: Parser[Position] = positioned(success(new Positional {})) ^^ {
    _.pos
  }

  def integer: Parser[IntegerLiteralOQL] =
    positioned("""\d+""".r ^^ IntegerLiteralOQL)

  def number: Parser[ExpressionOQL] =
    positioned("""-?\d+(\.\d*)?""".r ^^ {
      case n if n contains '.' => FloatLiteralOQL(n)
      case n                   => IntegerLiteralOQL(n)
    })

  def string: Parser[StringLiteralOQL] =
    positioned(
      (("'" ~> """[^'\n]*""".r <~ "'") |
        ("\"" ~> """[^"\n]*""".r <~ "\"")) ^^ StringLiteralOQL)

  def ident: Parser[Ident] =
    positioned("""[a-zA-Z_$][a-zA-Z0-9_$]*""".r ^^ Ident)

  def query: Parser[QueryOQL] =
    ident ~ opt(project) ~ opt(select) ~ opt(group) ~ opt(order) ~ opt(restrict) ^^ {
      case e ~ p ~ s ~ g ~ o ~ r =>
        QueryOQL(e,
                 if (p isDefined) p.get else ProjectAllOQL,
                 s,
                 g,
                 o,
                 if (r isDefined) r.get._1 else None,
                 if (r isDefined) r.get._2 else None)
    }

  def project: Parser[ProjectExpressionOQL] =
    "{" ~> rep1(attributeProject) <~ "}" ^^ ProjectAttributesOQL |
      "." ~> attributeProject ^^ (p => ProjectAttributesOQL(List(p)))

  def attributeProject: Parser[ProjectExpressionOQL] =
    ident ~ "(" ~ ident ~ ")" ^^ {
      case a ~ _ ~ i ~ _ => AggregateAttributeOQL(a, i)
    } |
      query

  def variable: Parser[VariableExpressionOQL] = rep1sep(ident, ".") ^^ VariableExpressionOQL

  def expression: Parser[ExpressionOQL] = applyExpression

  def logicalExpression: Parser[ExpressionOQL] =
    orExpression

  def orExpression: Parser[ExpressionOQL] =
    andExpression ~ rep(("OR" | "or") ~> andExpression) ^^ {
      case expr ~ list =>
        list.foldLeft(expr) {
          case (l, r) => InfixExpressionOQL(l, "OR", r)
        }
    }

  def andExpression: Parser[ExpressionOQL] =
    comparisonExpression ~ rep(("AND" | "and") ~> comparisonExpression) ^^ {
      case expr ~ list =>
        list.foldLeft(expr) {
          case (l, r) => InfixExpressionOQL(l, "AND", r)
        }
    }

  def notExpression: Parser[ExpressionOQL] =
    ("NOT" | "not") ~> comparisonExpression ^^ (p => PrefixExpressionOQL("NOT", p)) |
      comparisonExpression

  def comparisonExpression: Parser[ExpressionOQL] =
    applyExpression ~ ("<=" | ">=" | "<" | ">" | "=" | "!=" | ("LIKE" | "like" | "ILIKE" | "ilike") | (("NOT" | "not") ~ ("LIKE" | "like" | "ILIKE" | "ilike")) ^^^ "NOT LIKE") ~ applyExpression ^^ {
      case l ~ o ~ r => InfixExpressionOQL(l, o, r)
    } |
      applyExpression ~ ((("IS" | "is") ~ ("NULL" | "null") ^^^ "IS NULL") | (("IS" | "is") ~ ("NOT" | "not") ~ ("NULL" | "null")) ^^^ "IS NOT NULL") ^^ {
        case l ~ o => PostfixExpressionOQL(l, o)
      } |
      applyExpression ~ ((("IN" | "in") ^^^ "IN") | (("NOT" | "not") ~ ("IN" | "in")) ^^^ "NOT IN") ~ expressions ^^ {
        case e ~ o ~ l => InExpressionOQL(e, o, l)
      } |
      applyExpression

  def expressions: Parser[List[ExpressionOQL]] = "(" ~> rep1sep(expression, ",") <~ ")"

  def applyExpression =
    ident ~ expressions ^^ {
      case i ~ es => ApplyExpressionOQL(i, es)
    } |
      primaryExpression

  def primaryExpression: Parser[ExpressionOQL] =
    number |
      string |
      rep1sep(ident, ".") <~ "#" ^^ ReferenceExpressionOQL |
      variable |
      caseExpression |
      "(" ~> logicalExpression <~ ")" ^^ GroupedExpressionOQL

  def caseExpression =
    ("CASE" | "case") ~ rep1(when) ~ opt(("ELSE" | "else") ~> expression) ~ ("END" | "end") ^^ {
      case _ ~ ws ~ e ~ _ => CaseExpressionOQL(ws, e)
    }

  def when: Parser[(ExpressionOQL, ExpressionOQL)] =
    ("WHEN" | "when") ~ logicalExpression ~ ("THEN" | "then") ~ expression ^^ {
      case _ ~ c ~ _ ~ r => (c, r)
    }

  def select: Parser[ExpressionOQL] = "[" ~> logicalExpression <~ "]"

  def order: Parser[List[(ExpressionOQL, Boolean)]] = "<" ~> orderExpressions <~ ">"

  def orderExpressions: Parser[List[(ExpressionOQL, Boolean)]] = rep1sep(orderExpression, ",")

  def orderExpression: Parser[(ExpressionOQL, Boolean)] = expression ~ opt("/" | "\\") ^^ {
    case e ~ (Some("/") | None) => (e, true)
    case e ~ _                  => (e, false)
  }

  def group: Parser[List[VariableExpressionOQL]] = "(" ~> variables <~ ")"

  def variables: Parser[List[VariableExpressionOQL]] = rep1sep(variable, ",")

  def restrict: Parser[(Option[Int], Option[Int])] =
    "|" ~> (integer ~ opt("," ~> integer)) <~ "|" ^^ {
      case l ~ o => (Some(l.n.toInt), o map (_.n.toInt))
    } |
      "|" ~> "," ~> integer <~ "|" ^^ (o => (None, Some(o.n.toInt)))

  def parseFromString[T](src: String, grammar: Parser[T]): T =
    parseAll(grammar, new CharSequenceReader(src)) match {
      case Success(tree, _)       => tree
      case NoSuccess(error, rest) => problem(rest.pos, error)
    }

}