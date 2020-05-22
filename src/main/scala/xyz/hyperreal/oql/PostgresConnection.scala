package xyz.hyperreal.oql

import typings.pg.mod.{Client, ClientConfig, QueryArrayConfig}
import typings.pg.pgStrings

import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}

import scala.concurrent.Future

@JSExportTopLevel("PostgresConnection")
class PostgresConnection(user: String, password: String) extends Connection {

  private val client = new Client(
    js.Dynamic
      .literal(user = user, password = password)
      .asInstanceOf[ClientConfig])

  client.connect

  def query(sql: String): ResultSet =
    new PostgresArrayResultSet(
      client
        .query[js.Array[js.Any], js.Any](QueryArrayConfig[js.Any](pgStrings.array, sql)))

  @JSExport
  def close(): Unit = client.end

}

// Alison COVID testing
// 514 293 1536
// 514 267 7912
// 5800 N ask for testing
