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
  val conn = new RDBConnection(readFile("examples/movie.tab"))
  val oql = new OQL(readFile("examples/movie.erd"))

  oql
    .json("movie { mov_title mov_dt_rel } [mov_id IN (904)]", conn)
    .onComplete {
      case Failure(exception) => throw exception
      case Success(value) =>
        println(value)
        conn.close()
    }

}
