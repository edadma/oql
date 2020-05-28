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
  val conn = new RDBConnection(readFile("examples/orders.tab"))
  val oql = new OQL(readFile("examples/orders.erd"))
//  val conn = new RDBConnection(readFile("examples/movie.tab"))
//  val oql = new OQL(readFile("examples/movie.erd"))
//  val conn = new RDBConnection(readFile("examples/student.tab"))
//  val oql = new OQL(readFile("examples/student.erd"))

//  oql.json("enrollment { student { name count(name) } } (student.name) <student.name>", conn).onComplete {
//  oql.json("class { name students { name laptop { make model } } } [name = 'Science']", conn).onComplete {
  oql
    .json("agent { agent_code orders } [agent_code < 'A002']", conn)
    .onComplete {
//  oql
//    .json("movie { mov_title directors { dir_fname } } [mov_id < 903]", conn).onComplete {
      case Failure(exception) => throw exception
      case Success(value) =>
        println(value)
        conn.close()
    }

}
