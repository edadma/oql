package xyz.hyperreal

import scala.util.parsing.input.Position

package object oql {

  def problem(pos: Position, error: String) =
    if (pos eq null)
      sys.error(error)
    else if (pos.line == 1)
      sys.error(s"$error\n${pos.longString}")
    else
      sys.error(s"${pos.line}: $error\n${pos.longString}")

}