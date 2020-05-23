package xyz.hyperreal.oql

import xyz.hyperreal.rdb_sjs
import xyz.hyperreal.rdb_sjs.RelationResult

class RDBConnection(data: String) extends Connection {

  private val client = new rdb_sjs.Connection { load(data, doubleSpaces = true) }

  def query(sql: String): ResultSet =
    new BasicResultSet(
      client
        .executeSQLStatement(sql)
        .asInstanceOf[RelationResult]
        .relation
        .iterator)

  def close(): Unit = {}

}
