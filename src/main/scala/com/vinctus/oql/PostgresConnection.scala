package com.vinctus.oql

import typings.pg.mod.{Client, ClientConfig, QueryArrayConfig}
import typings.pg.pgStrings

import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("PostgresConnection")
class PostgresConnection(host: String, port: Double, database: String, user: String, password: String, ssl: Boolean)
    extends Connection {

  private val client = new Client(
    js.Dynamic
      .literal(host = host, port = port, database = database, user = user, password = password, ssl = ssl)
      .asInstanceOf[ClientConfig])

  client.connect()

  def query(sql: String): ResultSet =
    new PostgresArrayResultSet(
      client
        .query[js.Array[js.Any], js.Any](QueryArrayConfig[js.Any](pgStrings.array, sql)))

  @JSExport
  def close(): Unit = client.end()

}
