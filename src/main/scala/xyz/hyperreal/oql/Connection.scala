package xyz.hyperreal.oql

import scala.scalajs.js

trait Connection {

  def query(sql: String): js.Promise[js.Array[js.Any]]

}
