package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.concurrent.Future

abstract class Connection {

  def command(sql: String): ResultSet

  def raw(sql: String, values: Array[Any] = Array()): Future[Array[Any]]

  def close(): Unit

}
