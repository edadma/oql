package com.vinctus

import java.time.LocalDate

import scala.scalajs.js
import js.JSConverters._
import scala.collection.immutable.ListMap
import scala.scalajs.js.{JSON, |}
import scala.concurrent.Future
import scala.util.parsing.input.Position
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

package object oql {

  private val varRegex = ":([a-zA-Z]+)" r
  private val specialRegex = """(['\\\r\n])""" r

  def template(s: String, vars: Map[String, Any]): String = // todo: unit tests for parameters
    if (vars eq null)
      s
    else
      varRegex.replaceAllIn(
        s,
        m =>
          vars get m.group(1) match {
            case None        => sys.error(s"template: parameter '${m.group(1)}' not found")
            case Some(value) => Regex.quoteReplacement(render(value))
        }
      )

  def render(a: Any): String =
    a match {
      case s: String              => s"'${quote(s)}'"
      case d: js.Date             => s"'${d.toISOString}'"
      case a: js.Array[Any]       => s"(${a map render mkString ","})"
      case a: collection.Seq[Any] => s"(${a map render mkString ","})"
      case a: Array[Any]          => s"(${a map render mkString ","})"
      case _                      => String.valueOf(a)
    }

  def quote(s: String): String =
    specialRegex.replaceAllIn(s, _.group(1) match {
      case "'"  => "''"
      case "\\" => """\\\\"""
      case "\r" => """\\r"""
      case "\n" => """\\n"""
    })

  def toMap(obj: js.Any): ListMap[String, Any] = {
    def toMap(obj: js.Any): ListMap[String, Any] = {
      var map: ListMap[String, Any] = obj.asInstanceOf[js.Dictionary[js.Any]].to(ListMap)

      for ((k, v) <- map) {
        if ((v != null) && js.typeOf(v) == "object" && !v
              .isInstanceOf[Long] && !v.isInstanceOf[js.Date]) {
          map = map + ((k, toMap(v.asInstanceOf[js.Any])))
        }
      }

      map
    }

    if (obj == js.undefined) null
    else toMap(obj)
  }

  def toPromise[T](result: Future[T]): js.Promise[js.Any] = result map toJS toJSPromise

  def toPromiseOne[T](result: Future[Option[T]]): js.Promise[js.Any] = toPromise(result map (_.getOrElse(js.undefined)))

  def toJSON[T](result: Future[T]): Future[String] =
    result.map(value => JSON.stringify(toJS(value), null.asInstanceOf[js.Array[js.Any]], 2))

  def problem(pos: Position, error: String): Nothing =
    if (pos eq null)
      sys.error(error)
    else if (pos.line == 1)
      sys.error(s"$error\n${pos.longString}")
    else
      sys.error(s"${pos.line}: $error\n${pos.longString}")

  def toJS(a: Any): js.Any =
    a match {
      case Some(a) => a.asInstanceOf[js.Any]
      case None    => js.undefined
      case date: LocalDate =>
        val jsdate = new js.Date(date.getYear, date.getMonthValue - 1, date.getDayOfMonth)

        jsdate
      case d: BigDecimal => d.toDouble
      case l: Seq[_]     => l map toJS toJSArray
      case m: Map[_, _] =>
        (m map { case (k, v) => k -> toJS(v) })
          .asInstanceOf[Map[String, Any]]
          .toJSDictionary
      case _ => a.asInstanceOf[js.Any]
    }

//  def rowCount(oql: OQL, q: QueryOQL): Future[Int] =
//    new QueryBuilder(oql,
//                     q.copy(project =
//                              ProjectAttributesOQL(List(AggregateAttributeOQL(List(Ident("count")), Ident("*")))),
//                            order = None)).getOne map (_.get("count_*").asInstanceOf[Int])

}
