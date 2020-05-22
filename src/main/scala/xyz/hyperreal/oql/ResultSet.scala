package xyz.hyperreal.oql

import typings.pg.mod.QueryArrayResult

import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class ResultSet {
  def rowSet: Future[Iterator[ResultRow]]
}

class PostgresArrayResultSet(promise: js.Promise[QueryArrayResult[js.Array[js.Any]]]) extends ResultSet {
  def rowSet =
    promise.toFuture.map(result => result.rows.iterator map (new JSArrayResultRow(_)))
}

abstract class ResultRow {
  def apply(idx: Int): Any
}

class JSArrayResultRow(row: js.Array[js.Any]) extends ResultRow {
  def apply(idx: Int) = row(idx)
}
