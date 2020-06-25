package com.vinctus.oql

import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("Resource")
class Resource private[oql] (oql: OQL, name: String, entity: Entity) {
  def getMany() = {}
}
