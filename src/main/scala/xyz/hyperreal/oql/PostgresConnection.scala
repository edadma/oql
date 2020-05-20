package xyz.hyperreal.oql

import typings.pg.mod.{Client, ClientConfig, QueryArrayConfig, QueryResult}
import typings.pg.pgStrings

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.concurrent.Future

class PostgresConnection(user: String, password: String) extends Connection {

  private val client = new Client(
    js.Dynamic
      .literal(user = user, password = password)
      .asInstanceOf[ClientConfig])

  client.connect

  def query(sql: String): Future[ResultSet] =
    client
      .query[js.Array[js.Any], js.Any](QueryArrayConfig[js.Any](pgStrings.array, sql))
      .toFuture
      .map(r => new ResultSet(r.rows))

  def close(): Unit = client.end

}
