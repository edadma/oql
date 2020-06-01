package xyz.hyperreal

import java.time.LocalDate

import scala.scalajs.js
import js.JSConverters._
import scala.util.parsing.input.Position

package object oql {

  def problem(pos: Position, error: String): Nothing =
    if (pos eq null)
      sys.error(error)
    else if (pos.line == 1)
      sys.error(s"$error\n${pos.longString}")
    else
      sys.error(s"${pos.line}: $error\n${pos.longString}")

  def toJS(a: Any): js.Any =
    a match {
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
