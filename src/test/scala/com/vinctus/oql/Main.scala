package com.vinctus.oql

import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import Testing._

import scala.collection.immutable.ListMap

object Main extends App {

  private val fs = g.require("fs")

  private def readFile(name: String) = {
    fs.readFileSync(name).toString
  }

//  val conn = new PostgresConnection("localhost", 5432, "shuttlecontrol", "shuttlecontrol", "shuttlecontrol", false)
//  val oql = new OQL(conn, "entity tenant (tenants) {*id: bigint domain: text}")

  //  val conn = new PostgresConnection("localhost", 5432, "postgres", "postgres", "docker", false)
//  val oql = new OQL(conn, readFile("test.erd"))

//  val conn = new RDBConnection(readFile("examples/star_trek.tab"))
//  val oql = new OQL(conn, readFile("examples/star_trek.erd"))

  val conn = new RDBConnection(null)
  val oql = new OQL(conn, readFile("examples/un.erd"))

  for {
    _ <- oql.create
    _ <- oql.country.insert(ListMap("name" -> "asdf"))
    _ <- oql.country.insert(ListMap("name" -> "zxcv"))
    country <- oql.json("country")
  } {
    println(country)
    conn.close()
  }

//  conn
//    .query("insert into t (a, b) values ('zxcv', 789) returning id")
//    .rowSet
//    .onComplete {
//      case Failure(exception) => throw exception
//      case Success(value) =>
//        println(value.next.apply(0))
//        conn.close()
//    }

//  val conn = new RDBConnection(readFile("examples/un.tab"))
//  val oql = new OQL(conn, readFile("examples/un.erd"))

//  oql
//    .json("t")
//    .json("rep { name country.name }")
//    .json("planet [name = :name]", Map("name" -> "Qo'noS"))
//    .json("tenant")

//  val conn = employeesDB
//  val oql = employeesER
//
//  oql
//    .json("employee { name manager.name } [job_title = 'PRESIDENT']")

  //  val conn = new PostgresConnection("postgres", "docker")

//  val conn = ordersDB
//  val oql = ordersER
//
//  oql
//    .json(
//      "order { sum(ord_amount) count(ord_amount) agent.agent_name } [ord_amount between 3000 and 4000] (agent.agent_name) <agent.agent_name>",
//      conn)
//    .json("order { ord_num &agent } [ord_amount between 3000 and 4000]")

//  val conn = studentDB
//  val oql = studentER
//
//  oql
//    .queryBuilder(conn)
//    .project("student", "name")
//    .add(oql.queryBuilder(conn).project("classes").order("name", "ASC"))
//    .json
//    .json("student { * classes { * students } <name> } [name = 'John']")
//    .json("enrollment { ^student { * classes } } [&class = 9]")

//    .onComplete {
//      case Failure(exception) => throw exception
//      case Success(value) =>
//        println(value)
//        conn.close()
//    }

}
