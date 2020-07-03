package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

class Resource private[oql] (oql: OQL, name: String, entity: Entity) {

  private val builder = oql.queryBuilder().project(name)

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

    // get sub-map of all column attributes that are required
    val attrsNoPK = entity.pk.fold(attrs)(attrs - _)
    val attrsRequired =
      attrsNoPK.filter {
        case (_, attr: EntityColumnAttribute) => attr.required
        case _                                => false
      } keySet

//        .asInstanceOf[ListMap[String, EntityColumnAttribute]]

    // check if object contains all required column attribute properties
    if (!attrsRequired.subsetOf(obj.keySet))
      sys.error(s"missing properties: ${attrsRequired.diff(obj.keySet) map (p => s"'$p'") mkString ", "}")

    val command = new StringBuilder
    // build list of values to insert
    val pairs =
      attrsNoPK flatMap {
        case (k, _: PrimitiveEntityAttribute) if obj contains k => List(k -> render(obj(k)))
        case (k, ObjectEntityAttribute(_, typ, entity, _)) if obj contains k =>
          entity.pk match {
            case None     => sys.error(s"entity '$typ' has no declared primary key attribute")
            case Some(pk) => List(k -> render(obj(k).asInstanceOf[ListMap[String, Any]](pk)))
          }
        case (k, _) => if (attrsRequired(k)) sys.error(s"attribute '$k' is required") else Nil
      }
    val (keys, values) = pairs.unzip

    // build insert command
    command append s"INSERT INTO ${entity.table} (${keys mkString ", "}) VALUES\n"
    command append s"  (${values mkString ", "})\n"

    entity.pk match {
      case None =>
      case Some(pk) =>
        command append s"  RETURNING $pk\n"
    }

    print(command.toString)

    // execute insert command (to get a future)
    oql.conn.command(command.toString).rows map (row =>
      entity.pk match {
        case None => obj
        case Some(pk) =>
          val res = obj + (pk -> row.next().apply(0))

          attrs map { case (k, _) => k -> res(k) } to ListMap
      })
  }

}
