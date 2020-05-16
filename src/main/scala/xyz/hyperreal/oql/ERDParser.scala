package xyz.hyperreal.oql

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

  override protected val whiteSpace: Regex = """(\s|;.*)+""".r

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

  def ident: Parser[Ident] =
    positioned("""[a-zA-Z_#$][a-zA-Z0-9_#$]*""".r ^^ Ident)

  def variable: Parser[VariableExpressionERD] = ident ^^ VariableExpressionERD

  def definition: Parser[ERDefinitionERD] = rep1(block) ^^ ERDefinitionERD

  def block: Parser[BlockERD] = typeBlock | entityBlock

  def typeBlock: Parser[TypeBlockERD] =
    "type" ~> ident ~ "=" ~ ident ~ ":" ~ condition ^^ {
      case n ~ _ ~ u ~ _ ~ c => TypeBlockERD(n, u, c)
    }

  def condition: Parser[ExpressionERD] = boolCondition

  def boolCondition: Parser[ExpressionERD] =
    orCondition ~ rep("and" ~> orCondition) ^^ {
      case first ~ rest =>
        rest.foldLeft(first) { case (l, r) => AndExpressionERD(l, r) }
    }

  def orCondition: Parser[ExpressionERD] =
    compCondition ~ rep("or" ~> compCondition) ^^ {
      case first ~ rest =>
        rest.foldLeft(first) { case (l, r) => OrExpressionERD(l, r) }
    }

  def compCondition: Parser[ExpressionERD] =
    positioned(notCondition ~ rep(("<" | "<=") ~ notCondition) ^^ {
      case first ~ Nil => first
      case first ~ rest =>
        ComparisonExpressionERD(first, rest map { case c ~ r => (c, r) })
    })

  def notCondition: Parser[ExpressionERD] =
    positioned("not" ~> primaryCondition ^^ NotExpressionERD) |
      primaryCondition

  def primaryCondition: Parser[ExpressionERD] = variable | number

  def entityBlock: Parser[EntityBlockERD] =
    "entity" ~ ident ~ "{" ~ rep1(field) ~ "}" ^^ {
      case _ ~ n ~ _ ~ fs ~ _ => EntityBlockERD(n, fs)
    }

  def field: Parser[EntityFieldERD] = opt("*") ~ ident ~ ":" ~ typeSpec ^^ {
    case None ~ n ~ _ ~ t      => EntityFieldERD(n, t, pk = false)
    case Some("*") ~ n ~ _ ~ t => EntityFieldERD(n, t, pk = true)
  }

  def typeSpec: Parser[TypeSpecifierERD] = ident ^^ SimpleTypeERD

  def parseFromString[T](src: String, grammar: Parser[T]): T =
    parseAll(grammar, new CharSequenceReader(src)) match {
      case Success(tree, _)       => tree
      case NoSuccess(error, rest) => problem(rest.pos, error)
    }

}

/*

type Language = text: _ is in ["English", "French", "Ongota"]

type Country = text: _ is in ["UK", "US", "JP"]

type PosInt = integer: _ > 0

entity movie {
 *mov_id: integer
  mov_title: text
  mov_year: integer
  mov_time: integer //PosInt
  mov_lang: Language
  mov_dt_rel: date
  mov_rel_country: text //Country
}

entity actor {
 *act_id: integer
  act_fname: text
  act_lname: text
  act_gender: text
}

entity movie_cast {
  actor (act_id): actor
  movie (mov_id): movie
  role: text
}

entity director {
  dir_fname: text
  dir_lname: text
  movies: [movie] <movie_direction>
}

entity genres {
  gen_title: text
}



 */
