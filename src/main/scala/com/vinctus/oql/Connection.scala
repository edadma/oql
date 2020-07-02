package com.vinctus.oql

abstract class Connection {

  def command(sql: String): ResultSet

  def close(): Unit

}
