package com.vinctus.oql

import typings.pg.mod.{Pool, PoolClient, PoolConfig, QueryArrayConfig, QueryResult}
import typings.pg.pgStrings

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import js.annotation.{JSExport, JSExportTopLevel}
import scala.collection.immutable.ListMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.Promise

@JSExportTopLevel("PostgresConnection")
class PostgresConnection(host: String, port: Double, database: String, user: String, password: String, ssl: js.Any)
    extends Connection {

  private val pool = new Pool(
    js.Dynamic
      .literal(host = host, port = port, database = database, user = user, password = password, ssl = ssl, max = 2)
      .asInstanceOf[PoolConfig])

  def command(sql: String): ResultSet =
    new PostgresArrayResultSet(
      pool
        .connect()
        .toFuture
        .flatMap(
          (client: PoolClient) =>
            client
              .query[js.Array[js.Any], js.Any](QueryArrayConfig[js.Any](pgStrings.array, sql))
              .toFuture
              .andThen(_ => client.release()))
        .toJSPromise
    )

  def raw(sql: String, values: Array[Any] = Array()) =
    pool
      .connect()
      .toFuture
      .flatMap(
        (client: PoolClient) =>
          client
            .query[js.Any, _](sql, values.toJSArray)
            .toFuture
            .andThen(_ => client.release()))
      .map(_.rows)

  @JSExport
  def close(): Unit = pool.end()

}
