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

//  val conn = employeesDB
//  val oql = employeesER
//
//  oql
//    .json("employee { name manager.name } [job_title = 'PRESIDENT']", conn)

  //  val conn = new PostgresConnection("postgres", "docker")

//  val conn = ordersDB
//  val oql = ordersER
//
//  oql
//    .json(
//      "order { sum(ord_amount) count(ord_amount) agent.agent_name } [ord_amount between 3000 and 4000] (agent.agent_name) <agent.agent_name>",
//      conn)
//    .json("order { ord_num &agent } [ord_amount between 3000 and 4000]", conn)

  val conn = studentDB
  val oql = studentER

  oql
//    .queryBuilder(conn)
//    .project("student", "name")
//    .add(oql.queryBuilder(conn).project("classes").order("name", true))
//    .json
//    .json("student { * classes { * students } <name> } [name = 'John']", conn)
    .json("enrollment { ^student { * classes } } [&class = 9]", conn)
    .onComplete {
      case Failure(exception) => throw exception
      case Success(value) =>
        println(value)
        conn.close()
    }

}
