package com.vinctus.oql

import typings.pg.mod.{Pool, PoolClient, PoolConfig, QueryArrayConfig}
import typings.pg.pgStrings

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import js.annotation.{JSExport, JSExportTopLevel}
import scala.concurrent.ExecutionContext.Implicits.global

@JSExportTopLevel("PostgresConnection")
class PostgresConnection(host: String,
                         port: Double,
                         database: String,
                         user: String,
                         password: String,
                         ssl: js.Any,
                         max: Int = 5)
    extends Connection {

  private val pool = new Pool(
    js.Dynamic
      .literal(host = host, port = port, database = database, user = user, password = password, ssl = ssl, max = max)
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

  def raw(sql: String, values: js.Array[js.Any]): js.Promise[js.Array[js.Any]] =
    pool
      .connect()
      .toFuture
      .flatMap(
        (client: PoolClient) =>
          client
            .query[js.Any, js.Any](sql, values.toJSArray)
            .toFuture
            .andThen(_ => client.release()))
      .map(_.rows)
      .toJSPromise

  @JSExport
  def close(): Unit = pool.end()

}
