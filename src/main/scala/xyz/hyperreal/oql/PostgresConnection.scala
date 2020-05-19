package xyz.hyperreal.oql

import typings.pg.mod.{Client, ClientConfig, QueryResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.scalajs.js
import js.JSConverters._

class PostgresConnection(user: String, password: String) extends Connection {

  private val client = new Client(
    js.Dynamic
      .literal(user = user, password = password)
      .asInstanceOf[ClientConfig])

  Await.ready(client.connect.toFuture, 200 millis)

  def query(sql: String): js.Promise[js.Array[js.Any]] =
    client
      .query[js.Any, js.Any](sql)
      .toFuture
      .map(_.rows)
      .toJSPromise

}
