package com.vinctus.oql

import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import Testing._

object Main extends App {

  private val fs = g.require("fs")

  private def readFile(name: String) = {
    fs.readFileSync(name).toString
  }

//  val conn = new PostgresConnection("postgres", "docker")
  val conn = ordersDB //new RDBConnection(readFile("examples/student.tab"))
  val oql = ordersER //new OQL(readFile("examples/student.erd"))

  oql
    .json("agent { * orders { sum(ord_amount) } } [working_area = 'Bangalore'] <agent_code>", conn)
    .onComplete {
      case Failure(exception) => throw exception
      case Success(value) =>
        println(value)
        conn.close()
    }

}
