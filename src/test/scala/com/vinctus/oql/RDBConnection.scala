package com.vinctus.oql

import xyz.hyperreal.rdb_sjs
import xyz.hyperreal.rdb_sjs.{CreateResult, InsertResult, RelationResult}

class RDBConnection(data: String) extends Connection {

  private val client =
    new rdb_sjs.Connection {
      if (data ne null)
        load(data, doubleSpaces = true)
    }

  def command(sql: String): ResultSet =
    new BasicResultSet(client.executeSQLStatement(sql) match {
      case CreateResult(_)          => Iterator()
      case RelationResult(relation) => relation.iterator
      case InsertResult(auto, _)    => Iterator(auto.head.values.toIndexedSeq)
    })

  def close(): Unit = {}

}
