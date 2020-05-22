package xyz.hyperreal.oql

abstract class Connection {

  def query(sql: String): ResultSet

  def close(): Unit

}
