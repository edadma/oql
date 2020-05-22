package xyz.hyperreal.oql

import scala.util.matching.Regex
import scala.util.parsing.combinator.RegexParsers
import scala.util.parsing.input.{CharSequenceReader, Positional}

object OQLParser {

  def parseQuery(query: String) = {
    val p = new OQLParser

    p.parseFromString(query, p.query)
  }

}

class OQLParser extends RegexParsers {

  override protected val whiteSpace: Regex = """(\s|;.*)+""".r

  def pos = positioned(success(new Positional {})) ^^ {
    _.pos
  }

  def integer: Parser[IntegerLiteralOQL] =
    positioned("""\d+""".r ^^ IntegerLiteralOQL)

  def number: Parser[ExpressionOQL] =
    positioned("""\-?\d+(\.\d*)?""".r ^^ {
      case n if n contains '.' => FloatLiteralOQL(n)
      case n                   => IntegerLiteralOQL(n)
    })

  def string: Parser[StringLiteralOQL] =
    positioned(
      (("'" ~> """[^'\n]*""".r <~ "'") |
        ("\"" ~> """[^"\n]*""".r <~ "\"")) ^^ StringLiteralOQL)

  def ident =
    positioned("""[a-zA-Z_#$][a-zA-Z0-9_#$]*""".r ^^ Ident)

  def query =
    ident ~ opt(project) ~ opt(select) ~ opt(order) ~ opt(restrict) ^^ {
      case r ~ p ~ s ~ o ~ g =>
        OQLQuery(r, if (p isDefined) p.get else ProjectAllOQL, s, o, if (g isDefined) g.get else (None, None))
    }

  def project: Parser[ProjectExpressionOQL] =
    "{" ~> rep1(attributeProject) <~ "}" ^^ ProjectAttributesOQL |
      "." ~> attributeProject ^^ (p => ProjectAttributesOQL(List(p)))

  def attributeProject = ident ~ opt(project) ^^ {
    case i ~ None    => AttributeOQL(i, ProjectAllOQL)
    case i ~ Some(p) => AttributeOQL(i, p)
  }

  def variable = rep1sep(ident, ".") ^^ VariableExpressionOQL

  def expression = primaryExpression

  def logicalExpression: Parser[ExpressionOQL] =
    orExpression

  def orExpression =
    andExpression ~ rep(("OR" | "or") ~> andExpression) ^^ {
      case expr ~ list =>
        list.foldLeft(expr) {
          case (l, r) => InfixExpressionOQL(l, "OR", r)
        }
    }

  def andExpression =
    comparisonExpression ~ rep(("AND" | "and") ~> comparisonExpression) ^^ {
      case expr ~ list =>
        list.foldLeft(expr) {
          case (l, r) => InfixExpressionOQL(l, "AND", r)
        }
    }

  def notExpression =
    ("NOT" | "not") ~> comparisonExpression ^^ (p => PrefixExpressionOQL("NOT", p)) |
      comparisonExpression

  def comparisonExpression =
    primaryExpression ~ ("<" | ">" | "<=" | ">=" | "=" | "!=") ~ primaryExpression ^^ {
      case l ~ o ~ r => InfixExpressionOQL(l, o, r)
    } |
      primaryExpression

  def primaryExpression =
    number |
      string |
      variable |
      "(" ~> logicalExpression <~ ")" ^^ GroupedExpressionOQL

  def select = "[" ~> logicalExpression <~ "]"

  def order = "<" ~> rep1sep(orderExpression, ",") <~ ">"

  def orderExpression = expression ~ opt("/" | "\\") ^^ {
    case e ~ (Some("/") | None) => (e, true)
    case e ~ _                  => (e, false)
  }

  def restrict =
    "(" ~> (integer ~ "," ~ opt(integer)) <~ ")" ^^ {
      case b ~ _ ~ e => (Some(b.n.toInt), e map (_.n.toInt))
    } |
      "(" ~> "," ~> integer <~ ")" ^^ (e => (None, Some(e.n.toInt)))

  def parseFromString[T](src: String, grammar: Parser[T]) =
    parseAll(grammar, new CharSequenceReader(src)) match {
      case Success(tree, _)       => tree
      case NoSuccess(error, rest) => problem(rest.pos, error)
    }

}
