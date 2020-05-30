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
//  val conn = new RDBConnection(readFile("examples/orders.tab"))
//  val oql = new OQL(readFile("examples/orders.erd"))
//  val conn = new RDBConnection(readFile("examples/movie.tab"))
//  val oql = new OQL(readFile("examples/movie.erd"))
//  val conn = new RDBConnection(readFile("examples/student.tab"))
//  val oql = new OQL(readFile("examples/student.erd"))
  val conn = new RDBConnection(readFile("examples/employees.tab"))
  val oql = new OQL(readFile("examples/employees.erd"))

  oql.json("employee { emp_name manager.emp_name } <emp_name> |3|", conn).onComplete {
//  oql.json("enrollment { student { name count(name) } } (student.name) <student.name>", conn).onComplete {
//  oql.json("enrollment { student.name class.name grade } <grade> |2, 3|", conn).onComplete {
//  oql.json("class { name students { name } } [name ~ 'S%']", conn).onComplete {
//  oql
//    .json("agent { agent_code orders { ord_num customer.name } } [agent_code = 'A003']", conn)
//    .onComplete {
//  oql
//    .json("movie { mov_title directors { dir_fname } } [mov_id < 903]", conn).onComplete {
    case Failure(exception) => throw exception
    case Success(value) =>
      println(value)
      conn.close()
  }

}
