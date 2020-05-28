package xyz.hyperreal.oql

class Entity(var pk: Option[String], var attributes: Map[String, EntityAttribute])

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
