package com.vinctus.oql

import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.util.parsing.input.Position

class ERModel(defn: String) {

  private val entities: Map[String, Entity] = {
    val erDefinition = ERDParser.parseDefinition(defn)
    val entityMap = new mutable.HashMap[String, Entity]

    erDefinition.blocks foreach {
      case EntityBlockERD(entity, _, _) =>
        if (entityMap contains entity.name)
          problem(entity.pos, s"entity '${entity.name}' already defined")
        else
          entityMap(entity.name) = new Entity(null, null, null)
      case _ =>
    }

    erDefinition.blocks foreach {
      case EntityBlockERD(entity, actual, fields) =>
        var epk: String = null
        val attrs = mutable.LinkedHashMap.empty[String, EntityAttribute]

        for (EntityAttributeERD(attr, column, typ, pk) <- fields) {
          if (attrs contains attr.name)
            problem(attr.pos, s"attribute '${attr.name}' already exists for this entity'")
          else {
            val fieldtype =
              typ match {
                case SimpleTypeERD(typ) =>
                  entityMap get typ.name match {
                    case Some(e) =>
                      ObjectEntityAttribute(column.name, typ.name, e)
                    case None =>
                      PrimitiveEntityAttribute(column.name, typ.name)
                  }
                case JunctionArrayTypeERD(typ, junction) =>
                  (entityMap get typ.name, entityMap get junction.name) match {
                    case (Some(t), Some(j)) =>
                      ObjectArrayJunctionEntityAttribute(typ.name, t, junction.name, j)
                    case (None, _) =>
                      problem(typ.pos, s"not an entity: ${typ.name}")
                    case (_, None) =>
                      problem(junction.pos, s"not an entity: ${junction.name}")
                  }
                case ArrayTypeERD(typ) =>
                  entityMap get typ.name match {
                    case Some(t) =>
                      ObjectArrayEntityAttribute(typ.name, t)
                    case None =>
                      problem(typ.pos, s"not an entity: ${typ.name}")
                  }
              }

            attrs += (attr.name -> fieldtype)
          }

          if (pk) {
            if (epk ne null)
              problem(attr.pos, "there is already a primary key defined for this entity")
            else
              epk = column.name
          }
        }

        entityMap(entity.name).table = actual.name
        entityMap(entity.name).pk = if (epk ne null) Some(epk) else None
        entityMap(entity.name).attributes = attrs.to(ListMap)
//      case TypeBlockERD(name, underlying, condition) =>
    }

    entityMap.toMap
  }

  def get(table: String, pos: Position): Entity =
    entities get table match {
      case None    => problem(pos, s"unknown resource: '$table'")
      case Some(e) => e
    }

  def list(table: String, pos: Position): Seq[(String, EntityAttribute)] =
    get(table, pos).attributes.toList

}

// todo: check validity of entity types
// todo: implement boolean type; should be able to write 'a = true'
// todo: implement timestamp (without timezone); should be able to write 't <= timestamp'