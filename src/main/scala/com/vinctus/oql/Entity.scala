package com.vinctus.oql

import scala.collection.immutable.ListMap

class Entity(val name: String,
             var table: String,
             var pk: Option[String],
             var pkcolumn: Option[String],
             var attributes: ListMap[String, EntityAttribute])

sealed abstract class EntityAttribute { val typ: String }
case object AnyAttribute extends EntityAttribute { val typ: Null = null }
sealed abstract class EntityColumnAttribute extends EntityAttribute { val column: String; val required: Boolean }
case class PrimitiveEntityAttribute(column: String, typ: String, required: Boolean) extends EntityColumnAttribute
case class ObjectEntityAttribute(column: String, typ: String, entity: Entity, required: Boolean)
    extends EntityColumnAttribute
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
