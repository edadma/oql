package com.vinctus.oql

import typings.pg.mod.QueryArrayResult

import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class ResultSet {
  def rows: Future[Iterator[ResultRow]]
}

abstract class ResultRow {
  def apply(idx: Int): Any
}

class PostgresArrayResultSet(promise: js.Promise[QueryArrayResult[js.Array[js.Any]]]) extends ResultSet {
  def rows =
    promise.toFuture.map(result => result.rows.iterator map (new JSArrayResultRow(_)))
}

class JSArrayResultRow(row: js.Array[js.Any]) extends ResultRow {
  def apply(idx: Int) = row(idx)
}

class BasicResultSet(rows: Iterator[IndexedSeq[Any]]) extends ResultSet {
  def rows = Future { rows map (new BasicResultRow(_)) }
}

class BasicResultRow(row: IndexedSeq[Any]) extends ResultRow {
  def apply(idx: Int) = row(idx)
}
