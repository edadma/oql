package xyz.hyperreal.oql

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import js.JSConverters._
import js.JSON
import scala.concurrent.Future
import scala.util.Success

object OQL {

  def toJS(a: Any): js.Any =
    a match {
      case l: Seq[_] => l map toJS toJSArray
      case m: Map[_, _] =>
        (m map { case (k, v) => k -> toJS(v) })
          .asInstanceOf[Map[String, Any]]
          .toJSDictionary
      case _ => a.asInstanceOf[js.Any]
    }

  def pretty(res: Seq[Map[String, Any]]): String = JSON.stringify(toJS(res), null.asInstanceOf[js.Array[js.Any]], 2)

}

class OQL(erd: String) {

  private val model = new ERModel(erd)

  def query(s: String, conn: Connection): Future[List[Map[String, Any]]] = {
    val OQLQuery(resource, project, select, order, restrict) =
      OQLParser.parseQuery(s)
    val entity = model.get(resource.name, resource.pos)
    val projectbuf = new ListBuffer[(String, String)]
    val joinbuf = new ListBuffer[(String, String, String, String, String)]
    val graph = branches(resource.name, entity, project, projectbuf, joinbuf, List(resource.name))

    executeQuery(resource.name, select, order, restrict, entity, projectbuf, joinbuf, graph, conn)
  }

  private def executeQuery(resource: String,
                           select: Option[ExpressionOQL],
                           order: Option[List[(ExpressionOQL, Boolean)]],
                           restrict: (Option[Int], Option[Int]),
                           entity: Entity,
                           projectbuf: ListBuffer[(String, String)],
                           joinbuf: ListBuffer[(String, String, String, String, String)],
                           graph: Seq[ProjectionNode],
                           conn: Connection): Future[List[Map[String, Any]]] = {
    val sql = new StringBuilder

    sql append s"SELECT ${projectbuf map { case (e, f) => s"$e.$f" } mkString ", "}\n"
    sql append s"  FROM $resource"

    val where =
      if (select isDefined)
        expression(resource, entity, select.get, joinbuf)
      else
        null
    val orderby =
      if (order isDefined)
        order.get map {
          case (e, o) => s"(${expression(resource, entity, e, joinbuf)}) ${if (o) "ASC" else "DESC"}"
        } mkString ", "
      else
        null

    val joins = joinbuf.distinct

    if (joins nonEmpty) {
      val (lt, lf, rt, rta, rf) = joins.head

      sql append s" JOIN $rt AS $rta ON $lt.$lf = $rta.$rf\n"

      for ((lt, lf, rt, rta, rf) <- joins.tail)
        sql append s"    JOIN $rt AS $rta ON $lt.$lf = $rta.$rf\n"
    } else
      sql append '\n'

    if (select isDefined)
      sql append s"  WHERE $where\n"

    if (order isDefined)
      sql append s"  ORDER BY $orderby\n"

    print(sql)

    val projectmap = projectbuf.zipWithIndex.toMap

    conn
      .query(sql.toString)
      .flatMap(result => {
        val list = result.toList
        val futurebuf = new ListBuffer[Future[List[Map[String, Any]]]]

        list foreach (futures(_, futurebuf, projectmap, graph, conn))
        Future.sequence(futurebuf).map(_ => list map (build(_, projectmap, graph, conn)))
      })
  }

  private def expression(entityname: String,
                         entity: Entity,
                         expr: ExpressionOQL,
                         joinbuf: ListBuffer[(String, String, String, String, String)]) = {
    val buf = new StringBuilder

    def expression(expr: ExpressionOQL): Unit =
      expr match {
        case EqualsExpressionOQL(table, column, value) => buf append s"$table.$column = $value"
        case InfixExpressionOQL(left, op, right) =>
          expression(left)
          buf append s" $op "
          expression(right)
        case PrefixExpressionOQL(op, expr) =>
          buf append s" $op"
          expression(expr)
        case FloatLiteralOQL(n)         => buf append n
        case IntegerLiteralOQL(n)       => buf append n
        case StringLiteralOQL(s)        => buf append s"'$s'"
        case GroupedExpressionOQL(expr) => buf append s"($expr)"
        case VariableExpressionOQL(ids) => buf append reference(entityname, entity, ids, joinbuf)
      }

    expression(expr)
    buf.toString
  }

  private def reference(
      entityname: String,
      entity: Entity,
      ids: List[Ident],
      joinbuf: ListBuffer[(String, String, String, String, String)],
  ) = {
    def reference(entityname: String, entity: Entity, ids: List[Ident], attrlist: List[String]): String =
      ids match {
        case attr :: tail =>
          entity.attributes get attr.name match {
            case None =>
              problem(attr.pos, s"resource '$entityname' doesn't have an attribute '${attr.name}'")
            case Some(PrimitiveEntityAttribute(column, _)) =>
              s"${attrlist mkString "$"}.$column"
            case Some(ObjectEntityAttribute(column, entityType, entity)) =>
              if (tail == Nil)
                problem(attr.pos, s"attribute '${attr.name}' has non-primitive data type")
              else {
                val attrlist1 = column :: attrlist

                joinbuf += ((attrlist mkString "$", column, entityType, attrlist1 mkString "$", entity.pk.get))
                reference(entityType, entity, tail, attrlist1)
              }
          }
      }

    reference(entityname, entity, ids, List(entityname))
  }

  private def branches(entityname: String,
                       entity: Entity,
                       project: ProjectExpressionOQL,
                       projectbuf: ListBuffer[(String, String)],
                       joinbuf: ListBuffer[(String, String, String, String, String)],
                       attrlist: List[String]): Seq[ProjectionNode] = {
    val attrs =
      if (project == ProjectAllOQL) {
        entity.attributes map { case (k, v) => (k, v, ProjectAllOQL) } toList
      } else {
        project.asInstanceOf[ProjectAttributesOQL].attrs map (attr =>
          entity.attributes get attr.attr.name match {
            case None =>
              problem(attr.attr.pos, s"unknown attribute: '${attr.attr.name}'")
            case Some(typ) => (attr.attr.name, typ, attr.project)
          })
      }
    val table = attrlist mkString "$"

    if (entity.pk.isDefined && !attrs.exists(_._1 == entity.pk.get))
      projectbuf += table -> entity.pk.get

    attrs map {
      case (field, attr: PrimitiveEntityAttribute, _) =>
        projectbuf += (table -> field)
        PrimitiveProjectionNode(table, field, attr)
      case (field, attr: ObjectEntityAttribute, project) =>
        if (attr.entity.pk isEmpty)
          problem(null, s"entity '${attr.entityType}' is referenced as a type but has no primary key")

        val attrlist1 = field :: attrlist

        joinbuf += ((table, attr.column, attr.entityType, attrlist1 mkString "$", attr.entity.pk.get))
        EntityProjectionNode(field, branches(attr.entityType, attr.entity, project, projectbuf, joinbuf, attrlist1))
      case (field, ObjectArrayEntityAttribute(entityType, attrEntity, junctionType, junction), project) =>
        val projectbuf = new ListBuffer[(String, String)]
        val subjoinbuf = new ListBuffer[(String, String, String, String, String)]
        val ts = junction.attributes.toList.filter(
          a =>
            a._2
              .isInstanceOf[ObjectEntityAttribute] && a._2.asInstanceOf[ObjectEntityAttribute].entity == attrEntity)
        val junctionAttr =
          ts.length match {
            case 0 => problem(null, s"'$junctionType' does not contain an attribute of type '$entityType'")
            case 1 => ts.head._1 //_2.asInstanceOf[ObjectEntityAttribute].column
            case _ => problem(null, s"'$junctionType' contains more than one attribute of type '$entityType'")
          }
        val es = junction.attributes.toList.filter(
          a =>
            a._2
              .isInstanceOf[ObjectEntityAttribute] && a._2.asInstanceOf[ObjectEntityAttribute].entity == entity)
        val column =
          es.length match {
            case 0 => problem(null, s"does not contain an attribute of type '$entityname'")
            case 1 => es.head._2.asInstanceOf[ObjectEntityAttribute].column
            case _ => problem(null, s"contains more than one attribute of type '$entityname'")
          }

        EntityArrayProjectionNode(
          field,
          table,
          entity.pk.get, // used to be attrEntity.pk.get
          projectbuf,
          subjoinbuf,
          junctionType,
          column,
          junction,
          branches(junctionType,
                   junction,
                   ProjectAttributesOQL(List(AttributeOQL(Ident(junctionAttr), project))),
                   projectbuf,
                   subjoinbuf,
                   List(junctionType))
        )
    }
  }

  private def build(row: Connection#Row,
                    projectmap: Map[(String, String), Int],
                    branches: Seq[ProjectionNode],
                    conn: Connection) = {
    def build(branches: Seq[ProjectionNode]): Map[String, Any] = {
      (branches map {
        case EntityProjectionNode(field, branches)      => field -> build(branches)
        case PrimitiveProjectionNode(table, field, typ) => field -> row.get(table, field, projectmap)
        case node @ EntityArrayProjectionNode(field,
                                              tabpk,
                                              colpk,
                                              subprojectbuf,
                                              subjoinbuf,
                                              resource,
                                              column,
                                              entity,
                                              branches) =>
          node.future.value match {
            case Some(Success(value)) => field -> value
            case None                 => sys.error(s"query failed to execute: $field, $tabpk, $colpk, $branches")
          }
      }) toMap
    }

    build(branches)
  }

  private def futures(row: Connection#Row,
                      futurebuf: ListBuffer[Future[List[Map[String, Any]]]],
                      projectmap: Map[(String, String), Int],
                      branches: Seq[ProjectionNode],
                      conn: Connection) = {
    def futures(branches: Seq[ProjectionNode]): Unit = {
      branches foreach {
        case EntityProjectionNode(field, branches)      => futures(branches)
        case PrimitiveProjectionNode(table, field, typ) =>
        case node @ EntityArrayProjectionNode(field,
                                              tabpk,
                                              colpk,
                                              subprojectbuf,
                                              subjoinbuf,
                                              resource,
                                              column,
                                              entity,
                                              branches) =>
          node.future = executeQuery(
            resource,
            Some(EqualsExpressionOQL(resource, column, row.get(tabpk, colpk, projectmap).toString)),
            None,
            (None, None),
            entity,
            subprojectbuf,
            subjoinbuf,
            branches,
            conn
          )
          futurebuf += node.future
      }
    }

    futures(branches)
  }

  abstract class ProjectionNode { val field: String }
  case class PrimitiveProjectionNode(table: String, field: String, typ: EntityAttribute) extends ProjectionNode
  case class EntityProjectionNode(field: String, branches: Seq[ProjectionNode]) extends ProjectionNode
  case class EntityArrayProjectionNode(field: String,
                                       tabpk: String,
                                       colpk: String,
                                       subprojectbuf: ListBuffer[(String, String)],
                                       subjoinbuf: ListBuffer[(String, String, String, String, String)],
                                       resource: String,
                                       column: String,
                                       entity: Entity,
                                       branches: Seq[ProjectionNode])
      extends ProjectionNode { var future: Future[List[Map[String, Any]]] = _ }

}
