package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

class Resource private[oql] (oql: OQL, name: String, entity: Entity) {

  private val builder = oql.queryBuilder.project(name)

  @JSExport
  def getMany(): Future[List[ListMap[String, Any]]] = builder.getMany

  def insert(obj: collection.Map[String, Any]): js.Any = {
    // check if the object has a primary key
    entity.pk match {
      case None     =>
      case Some(pk) =>
        // object being inserted should not have a primary key property
        if (obj.contains(pk))
          sys.error(s"Resource.insert: object has a primary key property: $pk = ${obj(pk)}")
    }

    // get sub-map of all column attributes
    val attrs =
      entity.attributes
        .filter {
          case (name, _: EntityColumnAttribute) if entity.pk.isEmpty || entity.pk.get != name => true
          case _                                                                              => false
        }
        .asInstanceOf[ListMap[String, EntityColumnAttribute]]

    // check if object contains all necessary column attribute properties
    if (!attrs.keySet.subsetOf(obj.keySet))
      sys.error(s"missing properties: ${attrs.keySet.diff(obj.keySet) mkString ", "}")

    // build insert command
    val command = new StringBuilder
    val values =
      attrs map {
        case (k, _: PrimitiveEntityAttribute) => render(obj(k))
        case (k, ObjectEntityAttribute(_, typ, entity)) =>
          entity.pk match {
            case None     => sys.error(s"entity '$typ' has no declared primary key attribute")
            case Some(pk) => render(obj(k).asInstanceOf[ListMap[String, Any]](pk))
          }
      }

    command append s"INSERT INTO ${entity.table} (${attrs.values map (_.column) mkString ", "}) VALUES\n"
    command append s"  (${values mkString ", "})\n"
  }

}
