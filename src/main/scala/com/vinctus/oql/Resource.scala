package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

class Resource private[oql] (oql: OQL, name: String, entity: Entity) {

  private val builder = oql.queryBuilder.project(name)

  @JSExport("getMany")
  def jsGetMany(): js.Promise[js.Any] = builder.jsGetMany()

  def getMany: Future[List[ListMap[String, Any]]] = builder.getMany

  @JSExport("insert")
  def jsInsert(obj: js.Any): js.Promise[js.Any] =
    toPromise(insert(obj.asInstanceOf[js.Dictionary[js.Any]].to(ListMap)))

  def insert(obj: Map[String, Any]): Future[Any] = {
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
          case (_, _: EntityColumnAttribute) => true
          case _                             => false
        }
        .asInstanceOf[ListMap[String, EntityColumnAttribute]]

    val attrsNoPK = entity.pk.fold(attrs)(attrs - _)

    // check if object contains all necessary column attribute properties
    if (!attrsNoPK.keySet.subsetOf(obj.keySet))
      sys.error(s"missing properties: ${attrsNoPK.keySet.diff(obj.keySet) map (p => s"'$p'") mkString ", "}")

    val command = new StringBuilder
    // build list of values to insert
    val values =
      attrsNoPK map {
        case (k, _: PrimitiveEntityAttribute) => render(obj(k))
        case (k, ObjectEntityAttribute(_, typ, entity)) =>
          entity.pk match {
            case None     => sys.error(s"entity '$typ' has no declared primary key attribute")
            case Some(pk) => render(obj(k).asInstanceOf[ListMap[String, Any]](pk))
          }
      }

    // build insert command
    command append s"INSERT INTO ${entity.table} (${attrsNoPK.values map (_.column) mkString ", "}) VALUES\n"
    command append s"  (${values mkString ", "})\n"

    entity.pk match {
      case None =>
      case Some(pk) =>
        command append s"  RETURNING $pk\n"
    }

    // execute insert command (to get a future)
    oql.conn.query(command.toString).rowSet map (row =>
      entity.pk match {
        case None => obj
        case Some(pk) =>
          val res = obj + (pk -> row.next().apply(0))

          attrs map { case (k, _) => k -> res(k) } to ListMap
      })
  }

}
