package com.vinctus.oql

import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import typings.pg.mod.types.setTypeParser

import scala.collection.immutable.ListMap

object Main extends App {

  private val fs = g.require("fs")

  private def readFile(name: String) = {
    fs.readFileSync(name).toString
  }

  setTypeParser(20, (s: Any) => s.asInstanceOf[String].toDouble)

  val conn = new PostgresConnection("localhost", 5432, "shuttlecontrol", "shuttlecontrol", "shuttlecontrol", false)
  val oql = new OQL(conn, readFile("sc.erd"))

//  val conn = new PostgresConnection("localhost", 5432, "postgres", "postgres", "docker", false)
//  val oql = new OQL(conn, readFile("test.erd"))

//  val conn = new RDBConnection(readFile("examples/student.tab"))
//  val oql = new OQL(conn, readFile("examples/student.erd"))
//
//  for {
//    //r1 <- oql.user.insert(Map("firstName" -> "asdf", "lastName" -> "zxcv", "user_type" -> "RegularUser", "tenant" -> 1))
//    r1 <- oql.student.link(2, "classes", 3)
//    r2 <- oql.json("enrollment [student.name = 'Debbie']")
//  } {
//    println(r1, r2)
//    conn.close()
//  }

//  val conn = new RDBConnection(null)
//  val oql = new OQL(conn, readFile("sc.erd"))
//
////  oql.tenant.insert(Map("domain" -> "asdf", "active" -> true, "createdAt" -> new js.Date)).onComplete {
////    case Failure(exception) => println(exception)
////    case Success(value)     => println(value)
////  }

  /*
  set() test
   */
//  for {
//    q1 <- oql.json("tenant")
//    t1opt <- oql.queryOne("tenant")
//    t1 <- t1opt
//    _ <- oql.tenant.set(t1("id"), Map("domain" -> "poop"))
//    q2 <- oql.json("tenant")
//  } {
//    println(q1, t1, q2)
//    conn.close()
//  }

  /*
  unlink() test
   */
//  oql.station.unlink(1, "users", 1).onComplete {
//    case Failure(exception) => println(exception)
//    case Success(value)     => println(value)
//  }

  for {
    q1 <- oql.json("station {id name users {id firstName lastName roles.roleName}}")
    _ <- oql.station.unlink(1, "users", 2)
    q2 <- oql.json("station {id name users {id firstName lastName roles.roleName}}")
  } {
    println(q1, q2)
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
//  .raw("select * from users where user_type = 'DriverUser'", js.undefined.asInstanceOf[js.Array[js.Any]])
//    .raw("select * from users where user_type = $1", js.Array("DriverUser"))
//    .toFuture
//    .map(js.JSON.stringify(_, null.asInstanceOf[js.Array[js.Any]], 2)) //_.toArray.toList.map(_.asInstanceOf[js.Dictionary[String]])
//    .json("tenant [exists(stations [exists(users [email = 'cedrick+admin@shuttlecontrol.com'])])]")
//    .json("station [exists(users [email = 'cedrick+admin@shuttlecontrol.com'])]")
//    .json("rep { name country.name }")
//    .json("planet [name = :name]", Map("name" -> "Qo'noS"))
//    .json("trip {createdTime} [createdTime >= current_date - interval '30 days']")

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
