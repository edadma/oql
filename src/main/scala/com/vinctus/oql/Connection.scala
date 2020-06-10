package com.vinctus.oql

abstract class Connection {

  def query(sql: String): ResultSet

  def close(): Unit

}
