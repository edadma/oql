package xyz.hyperreal.oql

import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

object Main extends App {

  private val fs = g.require("fs")

  private def readFile(name: String) = {
    fs.readFileSync(name).toString
  }

//  val conn = new PostgresConnection("postgres", "docker")
  val conn = new RDBConnection(readFile("examples/student.tab"))
  val oql = new OQL(readFile("examples/student.erd"))

  oql
    .json("enrollment { student { name count(name) } } [student# = 1] (student.name)", conn)
    .onComplete {
      case Failure(exception) => throw exception
      case Success(value) =>
        println(value)
        conn.close()
    }

}
