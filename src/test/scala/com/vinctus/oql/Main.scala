package com.vinctus.oql

import scala.scalajs.js
import js.Dynamic.{global => g}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import typings.pg.mod.types.setTypeParser

object Main extends App {

  private val fs = g.require("fs")

  private def readFile(name: String) = {
    fs.readFileSync(name).toString
  }

  val conn = new RDBConnection(readFile("m2o.tab"))
  val oql = new OQL(conn, readFile("m2o.erd"))

  oql.trace = true

  for {
    q <- oql.json("y {id xs} [EXISTS (xs [a = 'zxcv'])]")
  } {
    println(q)
    conn.close()
  }

  //  setTypeParser(20, (s: Any) => s.asInstanceOf[String].toDouble)
//
//  val conn = new PostgresConnection("localhost", 5432, "mobility", "mobility", "mobility", false)
//  val oql = new OQL(conn, readFile("mobility.erd"))
//
//  for {
////    q1 <- oql.json("vehicle {driver.firstName}")
//    q1 <- oql.json("user {vehicle {type}}")
////    _ <- oql.vehicle
////      .set("9d6143a4-10ea-4968-abb6-aac9a9770370", Map("driver" -> "1df5e6d8-2e16-4053-9f21-faaabc0c8d0f"))
////    q1 <- oql.json("user {firstName vehicle.driver.firstName} [role = 'DRIVER' and firstName = 'Edward']")
//  } {
//    println(q1)
////    println(q2)
//    conn.close()
//  }

  //  val conn = new PostgresConnection("localhost", 5432, "shuttlecontrol", "shuttlecontrol", "shuttlecontrol", false)
//  val oql = new OQL(conn, readFile("sc.erd"))

//  val conn = new PostgresConnection("localhost", 5432, "postgres", "postgres", "docker", false)
//  val oql = new OQL(conn, readFile("m2m.erd"))

//  val conn = new RDBConnection(readFile("examples/northwind.tab"))
//  val oql = new OQL(conn, readFile("examples/northwind.erd"))
//
//  oql.trace = true
//
//  for {
////    q1 <- oql.json("Suppliers {CompanyName} [Products]")
//    q1 <- oql.json("Products {SupplierID.CompanyName} [UnitPrice = 22]")
//  } {
//    println(q1)
//    conn.close()
//  }

//  val conn = new RDBConnection(
//    """
//      |t
//      | id: integer, pk  a: timestamp
//      | 1                2020-09-29T13:02:14.338Z
//      | 2                2020-09-29T13:02:35.699Z
//      |""".stripMargin
//  )
//  val oql = new OQL(conn,
//                    """
//      |entity t {
//      | *id: integer
//      |  a: date
//      |}
//      |""".stripMargin)

//  val conn = new RDBConnection(
//    """
//      |t
//      | id: integer, pk  a: text
//      | 1                asdf
//      | 2                zxcv
//      |""".stripMargin
//  )
//  val oql = new OQL(conn,
//                    """
//      |entity t {
//      | *id: integer
//      |  a: text
//      |}
//      |""".stripMargin)

  //  oql.tenant.insert(Map("domain" -> "asdf", "active" -> true, "createdAt" -> new js.Date)).onComplete {
//    case Failure(exception) => println(exception)
//    case Success(value)     => println(value)
//  }

  /*
  set() test
   */
//  for {
//    q1 <- oql.json("user [id = 7]")
//    _ <- oql.user.set(7, Map("firstName" -> "amoray", "lastName" -> "PREMIUM"))
//    q2 <- oql.json("user [id = 7]")
//  } {
//    println(q1, q2)
//    conn.close()
//  }

//  val qb = oql
//    .queryBuilder()
//    .query("""customer
//             |  {id firstName lastName email language phoneNumber station {id name} createdAt}
//             |  [station.id IN ('762225a5-b4ba-4933-8f3c-9092a25e8947', '68bd2f8b-3003-4e23-8f3c-a1f3ef0bd58b')]
//             |  <createdAt DESC>""".stripMargin)
//
//  for {
//    customers <- qb.offset(0).limit(10).getMany
//    count <- qb.getCount
//  } {
//    println(customers)
//    println(count)
//    conn.close()
//  }

  /*
  unlink() test
   */
//  oql.station.unlink(1, "users", 1).onComplete {
//    case Failure(exception) => println(exception)
//    case Success(value)     => println(value)
//  }

//  for {
//    q1 <- oql.json("station {id name users {id firstName lastName roles.roleName}}")
//    _ <- oql.station.unlink(1, "users", 2)
//    q2 <- oql.json("station {id name users {id firstName lastName roles.roleName}}")
//  } {
//    println(q1, q2)
//    conn.close()
//  }

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
