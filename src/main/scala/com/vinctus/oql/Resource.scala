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
      case Some(ObjectArrayJunctionEntityAttribute(_, otherEntity, junctionType, junction)) =>
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
      case Some(_) => sys.error(s"attribute '$attribute' is not many-to-many")
      case None    => sys.error(s"attribute '$attribute' does not exist on entity '$name'")
    }

  @JSExport("unlink")
  def jsUnlink(id1: js.Any, resource: String, id2: js.Any): js.Promise[Unit] = unlink(id1, resource, id2).toJSPromise

  def unlink(id1: Any, attribute: String, id2: Any): Future[Unit] =
    entity.attributes get attribute match {
      case Some(ObjectArrayJunctionEntityAttribute(_, otherEntity, junctionType, junction)) =>
        val thisCol =
          junction.attributes
            .find {
              case (_, attr) =>
                attr.isInstanceOf[ObjectEntityAttribute] && attr.asInstanceOf[ObjectEntityAttribute].entity == entity
            }
            .get
            ._2
            .asInstanceOf[ObjectEntityAttribute]
            .column

        val thatCol =
          junction.attributes
            .find {
              case (_, attr) =>
                attr
                  .isInstanceOf[ObjectEntityAttribute] && attr.asInstanceOf[ObjectEntityAttribute].entity == otherEntity
            }
            .get
            ._2
            .asInstanceOf[ObjectEntityAttribute]
            .column

        val command = new StringBuilder

        // build delete command
        command append s"DELETE FROM ${junction.table}\n"
        command append s"  WHERE $thisCol = ${render(id1)} AND $thatCol = ${render(id2)}\n"

        //print(command.toString)

        // execute update command (to get a future)
        oql.conn.command(command.toString).rows map (_ => ())
      case Some(_) => sys.error(s"attribute '$attribute' is not many-to-many")
      case None    => sys.error(s"attribute '$attribute' does not exist on entity '$name'")
    }

  @JSExport("delete")
  def jsDelete(id: js.Any): js.Promise[Unit] = delete(id).toJSPromise

  def delete(id: Any): Future[Unit] = {
    val command = new StringBuilder

    // build delete command
    command append s"DELETE FROM ${entity.table}\n"
    command append s"  WHERE ${entity.pk.get} = ${render(id)}\n"

    //print(command.toString)

    // execute update command (to get a future)
    oql.conn.command(command.toString).rows map (_ => ())
  }

  @JSExport("insert")
  def jsInsert(obj: js.Any): js.Promise[js.Any] = toPromise(insert(toMap(obj)))

  def insert(obj: collection.Map[String, Any]): Future[Any] = {
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

    // get key set of all attributes
    val allKeys = entity.attributes.keySet

    // check if object contains undefined attributes
    if ((keyset diff allKeys).nonEmpty)
      sys.error(s"undefined properties: ${(keyset diff allKeys) map (p => s"'$p'") mkString ", "}")

    // check if object contains all required column attribute properties
    if (!(attrsRequiredKeys subsetOf keyset))
      sys.error(s"missing required properties: ${attrsRequiredKeys.diff(keyset) map (p => s"'$p'") mkString ", "}")

    val command = new StringBuilder

    // build list of values to insert
    val pairs =
      attrsNoPK flatMap {
        case (k, _: PrimitiveEntityAttribute) if obj contains k => List(k -> render(obj(k)))
        case (k, ObjectEntityAttribute(_, typ, entity, _)) if obj contains k =>
          entity.pk match {
            case None => sys.error(s"entity '$typ' has no declared primary key attribute")
            case Some(pk) =>
              val v = obj(k)

              if (js.typeOf(v) == "object" && (v != null) && !v.isInstanceOf[Long] && !v.isInstanceOf[js.Date])
                List(k -> render(v.asInstanceOf[collection.Map[String, Any]](pk)))
              else
                List(k -> render(v))
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

    // print(command.toString)

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

  @JSExport("set")
  def jsSet(id: js.Any, updates: js.Any): js.Promise[Unit] = set(id, toMap(updates)).toJSPromise

  def set(id: Any, updates: collection.Map[String, Any]): Future[Unit] = {
    // check if updates has a primary key
    entity.pk match {
      case None     =>
      case Some(pk) =>
        // object being updated should not have it's primary key changed
        if (updates.contains(pk))
          sys.error(s"Resource.set: primary key can not be changed: $pk")
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

    // get updates key set
    val keyset = updates.keySet

    // check if object contains extrinsic attributes
    if ((keyset diff attrsNoPKKeys).nonEmpty)
      sys.error(s"extrinsic properties: ${(keyset diff attrsNoPKKeys) map (p => s"'$p'") mkString ", "}")

    // build list of attributes to update
    val pairs =
      updates map {
        case (k, v) => attrs(k).column -> render(v)
      }

    val command = new StringBuilder

    // build update command
    command append s"UPDATE ${entity.table}\n"
    command append s"  SET ${pairs map { case (k, v) => s"$k = $v" } mkString ", "}\n"
    command append s"  WHERE ${entity.pk.get} = ${render(id)}\n"

    //print(command.toString)

    // execute update command (to get a future)
    oql.conn.command(command.toString).rows map (_ => ())
  }

}

/*
    // check if updates has a primary key
    val id =
      entity.pk match {
        case None     => sys.error(s"Resource.set: entity $name has no primary key")
        case Some(pk) =>
          // object being updated should not have it's primary key changed
          if (!updates.contains(pk))
            sys.error(s"Resource.set: primary key must be given: $pk")

          updates(pk)
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

    // get updates key set
    val keyset = updates.keySet

    // check if object contains extrinsic attributes
    if ((keyset diff attrsNoPKKeys).nonEmpty)
      sys.error(s"extrinsic properties: ${(keyset diff attrsNoPKKeys) map (p => s"'$p'") mkString ", "}")

 */
