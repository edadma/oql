package com.vinctus

import java.time.LocalDate

import scala.scalajs.js
import js.JSConverters._
import scala.scalajs.js.JSON

import scala.concurrent.Future
import scala.util.parsing.input.Position
import scala.concurrent.ExecutionContext.Implicits.global

package object oql {

  def render(a: Any) =
    a match {
      case s: String => s"'$s'"
      case _         => String.valueOf(a)
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

}
