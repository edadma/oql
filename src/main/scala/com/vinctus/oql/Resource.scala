package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import js.JSConverters._

class Resource private[oql] (oql: OQL, name: String, entity: Entity) {

  private val builder = oql.queryBuilder().project(name)

  @JSExport("getMany")
  def jsGetMany(): js.Promise[js.Any] = builder.jsGetMany()

  def getMany: Future[List[ListMap[String, Any]]] = builder.getMany

  @JSExport("link")
  def jsLink(id1: js.Any, resource: String, id2: js.Any): js.Promise[Unit] = link(id1, resource, id2).toJSPromise

  def link(id1: Any, attribute: String, id2: Any): Future[Unit] =
    entity.attributes get attribute match {
      case Some(ObjectArrayJunctionEntityAttribute(entityType, otherEntity, junctionType, junction)) =>
        val thisAttr =
          junction.attributes
            .find {
              case (_, attr) =>
                attr.isInstanceOf[ObjectEntityAttribute] && attr.asInstanceOf[ObjectEntityAttribute].entity == entity
            }
            .get
            ._1
        val thatAttr =
          junction.attributes
            .find {
              case (_, attr) =>
                attr
                  .isInstanceOf[ObjectEntityAttribute] && attr.asInstanceOf[ObjectEntityAttribute].entity == otherEntity
            }
            .get
            ._1

        oql.entity(junctionType).insert(Map(thisAttr -> id1, thatAttr -> id2)) map (_ => ())
      case Some(_) => sys.error(s" attribute '$attribute' is not many-to-many")
      case None    => sys.error(s"attribute '$attribute' does not exist on entity '$name'")
    }

  @JSExport("insert")
  def jsInsert(obj: js.Any): js.Promise[js.Any] = toPromise(insert(toMap(obj)))

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

    // get sub-map of column attributes excluding primary key
    val attrsNoPK = entity.pk.fold(attrs)(attrs - _)

    // get key set of column attributes excluding primary key
    val attrsNoPKKeys = attrsNoPK.keySet

    // get key set of all column attributes that are required
    val attrsRequiredKeys =
      attrsNoPK.filter {
        case (_, attr: EntityColumnAttribute) => attr.required
        case _                                => false
      } keySet

    // get object's key set
    val keyset = obj.keySet

    // check if object contains extrinsic attributes
    if ((keyset diff entity.attributes.keySet).nonEmpty)
      sys.error(s"extrinsic properties: ${keyset.diff(attrsNoPKKeys) map (p => s"'$p'") mkString ", "}")

    // check if object contains all required column attribute properties
    if (!(attrsRequiredKeys subsetOf keyset))
      sys.error(s"missing properties: ${attrsRequiredKeys.diff(keyset) map (p => s"'$p'") mkString ", "}")

    val command = new StringBuilder

    // build list of values to insert
    val pairs =
      attrsNoPK flatMap {
        case (k, _: PrimitiveEntityAttribute) if obj contains k => List(k -> render(obj(k)))
        case (k, ObjectEntityAttribute(_, typ, entity, _)) if obj contains k =>
          entity.pk match {
            case None => sys.error(s"entity '$typ' has no declared primary key attribute")
            case Some(pk) =>
              if (!obj(k).isInstanceOf[Long] && js.typeOf(obj(k)) == "object")
                List(k -> render(obj(k).asInstanceOf[collection.Map[String, Any]](pk)))
              else
                List(k -> render(obj(k)))
          }
        case (k, _) => if (attrsRequiredKeys(k)) sys.error(s"attribute '$k' is required") else Nil
      }

    // check for empty insert
    if (pairs.isEmpty)
      sys.error("empty insert")

    val (keys, values) = pairs.unzip

    // transform list of keys into un-aliased column names
    val columns = keys map (k => attrs(k).column)

    // build insert command
    command append s"INSERT INTO ${entity.table} (${columns mkString ", "}) VALUES\n"
    command append s"  (${values mkString ", "})\n"

    entity.pk match {
      case None =>
      case Some(pk) =>
        command append s"  RETURNING $pk\n"
    }

    //print(command.toString)

    // execute insert command (to get a future)
    oql.conn.command(command.toString).rows map (row => {
      entity.pk match {
        case None => obj
        case Some(pk) =>
          val res = obj + (pk -> row.next().apply(0))

          attrs map { case (k, _) => k -> res.getOrElse(k, null) } to ListMap
      }
    })
  }

}
