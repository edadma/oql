package xyz.hyperreal.oql

import scala.collection.immutable.ListMap

class Entity(var table: String, var pk: Option[String], var attributes: ListMap[String, EntityAttribute])

abstract class EntityAttribute { val typ: String }
case class PrimitiveEntityAttribute(column: String, typ: String) extends EntityAttribute
case class ObjectEntityAttribute(column: String, typ: String, entity: Entity) extends EntityAttribute
case class ObjectArrayJunctionEntityAttribute(entityType: String,
                                              entity: Entity,
                                              junctionType: String,
                                              junction: Entity)
    extends EntityAttribute { val typ = s"[$entityType]" }
case class ObjectArrayEntityAttribute(entityType: String, entity: Entity) extends EntityAttribute {
  val typ = s"[$entityType]"
}
