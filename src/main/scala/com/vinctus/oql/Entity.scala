package com.vinctus.oql

import scala.collection.immutable.ListMap

class Entity(var table: String, var pk: Option[String], var attributes: ListMap[String, EntityAttribute]) {}

sealed abstract class EntityAttribute { val typ: String }
sealed abstract class EntityColumnAttribute extends EntityAttribute { val column: String }
case class PrimitiveEntityAttribute(column: String, typ: String) extends EntityColumnAttribute
case class ObjectEntityAttribute(column: String, typ: String, entity: Entity) extends EntityColumnAttribute
case class ObjectOneEntityAttribute(typ: String, entity: Entity, attr: Option[String]) extends EntityAttribute
case class ObjectArrayJunctionEntityAttribute(entityType: String,
                                              entity: Entity,
                                              junctionType: String,
                                              junction: Entity)
    extends EntityAttribute { val typ = s"[$entityType]" }
case class ObjectArrayEntityAttribute(entityType: String, entity: Entity) extends EntityAttribute {
  val typ = s"[$entityType]"
}
case class LiteralEntityAttribute(value: Any) extends EntityAttribute { val typ = "literal" }
