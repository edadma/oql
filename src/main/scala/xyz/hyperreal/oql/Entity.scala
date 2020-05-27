package xyz.hyperreal.oql

class Entity(var pk: Option[String], var attributes: Map[String, EntityAttribute])

abstract class EntityAttribute
case class PrimitiveEntityAttribute(column: String, primitiveType: String) extends EntityAttribute
case class ObjectEntityAttribute(column: String, entityType: String, entity: Entity) extends EntityAttribute
case class ObjectArrayJunctionEntityAttribute(entityType: String,
                                              entity: Entity,
                                              junctionType: String,
                                              junction: Entity)
    extends EntityAttribute
case class ObjectArrayEntityAttribute(entityType: String, entity: Entity) extends EntityAttribute
