package com.vinctus.oql

import typings.node.tlsMod.ConnectionOptions
import typings.pg.mod.{Pool, PoolClient, PoolConfig, QueryArrayConfig}

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import js.annotation.{JSExport, JSExportTopLevel}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js.|

@JSExportTopLevel("PostgresConnection")
class PostgresConnection(host: String,
                         port: Double,
                         database: String,
                         user: String,
                         password: String,
                         ssl: js.Any,
                         idleTimeoutMillis: Int,
                         max: Int)
    extends Connection {

  private val pool = new Pool(
    PoolConfig()
      .setHost(host)
      .setPort(port)
      .setDatabase(database)
      .setUser(user)
      .setPassword(password)
      .setSsl(ssl.asInstanceOf[Boolean | ConnectionOptions])
      .setIdleTimeoutMillis(idleTimeoutMillis)
      .setMax(max))

  def command(sql: String): ResultSet =
    new PostgresArrayResultSet(
      pool
        .connect()
        .toFuture
        .flatMap(
          (client: PoolClient) =>
            client
              .query[js.Array[js.Any], js.Any](QueryArrayConfig[js.Any](sql))
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
