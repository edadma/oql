package xyz.hyperreal.oql

import scala.scalajs.js

import scala.concurrent.Future

abstract class Connection {

  def query(sql: String): Future[ResultSet]

  def close(): Unit

  class ResultSet(rows: js.Array[js.Array[js.Any]]) extends Iterator[Row] {
    private val it = rows.iterator
    private var row: js.Array[js.Any] = _

    def hasNext: Boolean = it.hasNext

    def next: Row = new Row(it.next)
  }

  class Row protected[Connection] (row: js.Array[js.Any]) {
    def get(table: String, column: String, project: Map[(String, String), Int]): js.Any = row(project(table, column))
  }

}
