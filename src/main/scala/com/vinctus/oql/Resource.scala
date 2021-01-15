package com.vinctus.oql

import com.vinctus.sjs_utils.DynamicMap

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import js.JSConverters._

class Resource private[oql] (oql: OQL, name: String, entity: Entity) {

  private val builder = oql.queryBuilder().project(name)

  @JSExport("getMany")
  def jsjsGetMany(): js.Promise[js.Any] = builder.jsjsGetMany()

  def getMany: Future[List[ListMap[String, Any]]] = builder.getMany

  def jsGetMany[T <: js.Object]: Future[T] = builder.jsGetMany

  @JSExport("link")
  def jsjsLink(e1: js.Any, resource: String, e2: js.Any): js.Promise[Unit] = jsLink(e1, resource, e2).toJSPromise

  def jsLink(e1: js.Any, resource: String, e2: js.Any): Future[Unit] = {
    val id1 = if (jsObject(e1)) e1.asInstanceOf[js.Dictionary[String]](entity.pk.get) else e1
    val id2 = if (jsObject(e2)) e2.asInstanceOf[js.Dictionary[String]](entity.pk.get) else e2

    link(id1, resource, id2)
  }

  //todo: support multiple many-to-many relationships between the same two entities
  def link(id1: Any, attribute: String, id2: Any): Future[Unit] =
    entity.attributes get attribute match {
      case Some(ObjectArrayJunctionEntityAttribute(_, otherEntity, attrEntityAttr, junctionType, junction)) =>
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

        oql.entity(junctionType).insert(ListMap(thisAttr -> id1, thatAttr -> id2)) map (_ => ())
      case Some(_) => sys.error(s"attribute '$attribute' is not many-to-many")
      case None    => sys.error(s"attribute '$attribute' does not exist on entity '$name'")
    }

  @JSExport("unlink")
  def jsjsUnlink(e1: js.Any, resource: String, e2: js.Any): js.Promise[Unit] = jsUnlink(e1, resource, e2).toJSPromise

  def jsUnlink(e1: js.Any, resource: String, e2: js.Any): Future[Unit] = {
    val id1 = if (jsObject(e1)) e1.asInstanceOf[js.Dictionary[String]](entity.pk.get) else e1
    val id2 = if (jsObject(e2)) e2.asInstanceOf[js.Dictionary[String]](entity.pk.get) else e2

    unlink(id1, resource, id2)
  }

  //todo: support multiple many-to-many relationships between the same two entities
  def unlink(id1: Any, attribute: String, id2: Any): Future[Unit] =
    entity.attributes get attribute match {
      case Some(ObjectArrayJunctionEntityAttribute(_, otherEntity, attrEntityAttr, junctionType, junction)) =>
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

        if (oql.trace)
          println(command.toString)

        // execute update command (to get a future)
        oql.conn.command(command.toString).rows map (_ => ())
      case Some(_) => sys.error(s"attribute '$attribute' is not many-to-many")
      case None    => sys.error(s"attribute '$attribute' does not exist on entity '$name'")
    }

  @JSExport("delete")
  def jsjsDelete(e: js.Any): js.Promise[Unit] = jsDelete(e).toJSPromise

  def jsDelete(e: js.Any): Future[Unit] =
    delete(if (jsObject(e)) e.asInstanceOf[js.Dictionary[String]](entity.pk.get) else e)

  def delete(id: Any): Future[Unit] = {
    val command = new StringBuilder

    // build delete command
    command append s"DELETE FROM ${entity.table}\n"
    command append s"  WHERE ${entity.pkcolumn.get} = ${render(id)}\n"

    if (oql.trace)
      println(command.toString)

    // execute update command (to get a future)
    oql.conn.command(command.toString).rows map (_ => ())
  }

  @JSExport("insert")
  def jsjsInsert(obj: js.Any): js.Promise[js.Any] = toPromise(jsInsert(obj))

  def jsInsert(obj: js.Any): Future[Map[String, Any]] = insert(toMap(obj))

  def insert(obj: Map[String, Any]): Future[DynamicMap] = {
    // check if the object has a primary key
    entity.pk foreach { pk =>
      // object being inserted should not have a primary key property
      if (obj.contains(pk) && obj(pk) != js.undefined)
        sys.error(s"insert(): object has a primary key property: $pk = ${obj(pk)}")
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

    // get key set of all column attributes that are required
    val attrsRequired =
      attrsNoPK.filter {
        case (_, attr: EntityColumnAttribute) => attr.required
        case _                                => false
      } keySet

    // get object's key set
    val keyset = obj.keySet

    // get key set of all attributes
    val allKeys = attrs.keySet

    // check if object contains undefined attributes
    if ((keyset diff allKeys).nonEmpty)
      sys.error(
        s"insert(): found properties not defined for entity '$name': ${(keyset diff allKeys) map (p => s"'$p'") mkString ", "}")

    // check if object contains all required column attribute properties
    if (!(attrsRequired subsetOf keyset))
      sys.error(
        s"insert(): missing required properties for entity '$name': ${(attrsRequired diff keyset) map (p => s"'$p'") mkString ", "}")

    val command = new StringBuilder

    // build list of values to insert
    val pairs =
      attrsNoPK flatMap {
        case (k, _: PrimitiveEntityAttribute) if obj contains k => List(k -> render(obj(k)))
        case (k, ObjectEntityAttribute(_, typ, entity, _)) if obj contains k =>
          entity.pk match {
            case None => sys.error(s"entity '$typ' has no declared primary key")
            case Some(pk) =>
              val v = obj(k)

              List(k -> render(if (jsObject(v)) v.asInstanceOf[Map[String, Any]](pk) else v))
          }
        case (k, _) => if (attrsRequired(k)) sys.error(s"attribute '$k' is required") else Nil
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

    entity.pkcolumn foreach (pk => command append s"  RETURNING $pk\n")

    if (oql.trace)
      println(command.toString)

    // execute insert command (to get a future)
    oql.conn.command(command.toString).rows map (row =>
      new DynamicMap(entity.pk match {
        case None => obj to ListMap
        case Some(pk) =>
          val res = obj + (pk -> row
            .next()
            .apply(0)) // only one value is being requested: the primary key

          attrs map { case (k, _) => k -> res.getOrElse(k, null) } to ListMap
      }))
  }

  @JSExport("update")
  def jsjsUpdate(e: js.Any, updates: js.Any): js.Promise[Unit] = jsUpdate(e, updates).toJSPromise

  def jsUpdate(e: js.Any, updates: js.Any): Future[Unit] =
    update(if (jsObject(e)) e.asInstanceOf[js.Dictionary[String]](entity.pk.get) else e, toMap(updates))

  def update(e: Any, updates: collection.Map[String, Any]): Future[Unit] = {
    // check if updates has a primary key
    entity.pk foreach (pk =>
      // object being updated should not have it's primary key changed
      if (updates.contains(pk))
        sys.error(s"Resource.set: primary key can not be changed: $pk"))

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
        case (k, v) =>
          val v1 =
            if (jsObject(v))
              entity.attributes(k) match {
                case ObjectEntityAttribute(_, _, e, _) if e.pk.isDefined =>
                  v.asInstanceOf[Map[String, Any]](e.pk.get) // todo: unit test
                case ObjectEntityAttribute(_, _, e, _) =>
                  sys.error(s"entity '${e.name}' does not have a declared primary key")
                case _ => sys.error(s"attribute '$k' of entity '${entity.name}' is not an entity attribute")
              } else
              v

          attrs(k).column -> render(v1)
      }

    val command = new StringBuilder
    val id =
      e match {
        case m: Map[_, _] => m.asInstanceOf[Map[String, Any]].apply(entity.pk.get)
        case _            => e
      }

    // build update command
    command append s"UPDATE ${entity.table}\n"
    command append s"  SET ${pairs map { case (k, v) => s"$k = $v" } mkString ", "}\n"
    command append s"  WHERE ${entity.pkcolumn.get} = ${render(id)}\n"

    if (oql.trace)
      println(command.toString)

    // execute update command (to get a future)
    oql.conn.command(command.toString).rows map (_ => ())
  }

}
