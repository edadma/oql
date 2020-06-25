package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

class Resource private[oql] (oql: OQL, name: String, entity: Entity) {
  private val builder = oql.queryBuilder.project(name)

  @JSExport
  def getMany(): Future[List[ListMap[String, Any]]] = builder.getMany

  def insert(obj: js.Any): js.Any = {
    val map = obj.asInstanceOf[js.Dictionary[js.Any]]

    // check if the object has a primary key
    entity.pk match {
      case None =>
      case Some(pk) =>
        if (map.contains(pk))
          sys.error(s"Resource.insert: object has a primary key property: $pk = ${map(pk)}")
    }
  }
}
