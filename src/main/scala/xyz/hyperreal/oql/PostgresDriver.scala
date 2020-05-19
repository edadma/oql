package xyz.hyperreal.oql

import typings.pg.mod.{Client, ClientConfig, QueryResult}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Await
import scala.concurrent.duration._

import scala.scalajs.js
import js.JSConverters._

class PostgresDriver(user: String, password: String) {

  private val client = new Client(
    js.Dynamic
      .literal(user = user, password = password)
      .asInstanceOf[ClientConfig])

  private val connectFuture = client.connect().toFuture

  Await.ready(connectFuture, 200 millis)

  def query(sql: String): js.Promise[js.Array[js.Any]] =
    client
      .query(sql)
      .asInstanceOf[js.Promise[QueryResult[js.Any]]]
      .toFuture
      .map(_.rows)
      .toJSPromise

}
