package xyz.hyperreal.oql

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.parsing.input.Position
import scala.scalajs.js
import js.JSConverters._
import scala.scalajs.js.JSON

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

  def pretty(res: Seq[Map[String, Any]]): String =
    JSON.stringify(toJS(res), null.asInstanceOf[js.Array[js.Any]], 2)

}

class OQL(erd: String) {

  private val model = new ERModel(erd)

  def query(s: String, conn: Connection): Seq[Map[String, Any]] = {
    val OQLQuery(resource, project, select, order, group) =
      OQLParser.parseQuery(s)

    val projectbuf = new ListBuffer[(String, String)]
    val joinbuf = new ListBuffer[(String, String, String, String, String)]
    val graph: ProjectionBranch =
      branches(resource.name,
               resource.pos,
               project,
               projectbuf,
               joinbuf,
               List(resource.name))

    val sql = new StringBuilder
    val entityset = joinbuf.map(_._4).toSet + resource.name

    sql append s"SELECT ${projectbuf map { case (e, f) => s"$e.$f" } mkString ", "}\n"
    sql append s"  FROM ${resource.name}"

    for ((lt, lf, rt, rta, rf) <- joinbuf)
      sql append s" JOIN $rt AS $rta ON $lt.$lf = $rta.$rf"

    sql append '\n'

    def where(expr: ExpressionOQL): Unit =
      expr match {
        case InfixExpressionOQL(left, op, right) =>
          where(left)
          sql append s" $op "
          where(right)
        case PrefixExpressionOQL(op, expr) =>
          sql append s" $op"
          where(expr)
        case FloatLiteralOQL(n)         => sql append n
        case IntegerLiteralOQL(n)       => sql append n
        case StringLiteralOQL(s)        => sql append s"'$s'"
        case GroupedExpressionOQL(expr) => sql append s"($expr)"
        case v @ VariableExpressionOQL(ids) =>
          val ns = ids map (_.name)
          val entity = (resource.name :: ns.init).reverse mkString "$"
          val vselect = s"$entity.${ns.last}"

          if (!entityset(entity)) {
            problem(
              v.pos,
              s"object not found: ${(resource.name :: ns.init) mkString "."}")
          }

          sql append vselect
      }

    if (select isDefined) {
      sql append "  WHERE "
      where(select.get)
      sql append '\n'
    }

    //print(sql)
    val res =
      conn
        .executeSQLStatement(sql.toString)
        .asInstanceOf[RelationResult]
        .relation
        .collect

    res.toList map (build(_, res.metadata, graph))
  }

  private def branches(
      entity: String,
      pos: Position,
      project: ProjectExpressionOQL,
      projectbuf: ListBuffer[(String, String)],
      joinbuf: ListBuffer[(String, String, String, String, String)],
      attrbuf: List[String]): ObjectProjectionBranch = {
    val attrs =
      if (project == ProjectAllOQL) {
        model.list(entity, pos) map { case (k, v) => (k, v, ProjectAllOQL) }
      } else {
        val ent = model.get(entity, pos)

        project.asInstanceOf[ProjectAttributesOQL].attrs map (attr =>
          ent.attributes get attr.attr.name match {
            case None =>
              problem(pos, s"unknown attribute: '${attr.attr.name}'")
            case Some(typ) => (attr.attr.name, typ, attr.project)
          })
      }
    ObjectProjectionBranch(attrs map {
      case (field, attr: PrimitiveEntityAttribute, _) =>
        val e = attrbuf mkString "$"

        projectbuf += (e -> field)
        PrimitiveProjectionNode(e, field, attr)
      case (field, attr: ObjectEntityAttribute, project) =>
        if (attr.entity.pk isEmpty)
          problem(
            pos,
            s"entity '${attr.entityType}' is referenced as a type but has no primary key")

        val attrbuf1 = field :: attrbuf

        joinbuf += ((attrbuf mkString "$",
                     field,
                     attr.entityType,
                     attrbuf1 mkString "$",
                     attr.entity.pk.get))
        EntityProjectionNode(
          entity,
          field,
          branches(attr.entityType, pos, project, projectbuf, joinbuf, attrbuf1)
        )
    })
  }

  private def build(row: Tuple, md: Metadata, branch: ProjectionBranch) = {
    def build(branch: ProjectionBranch): Map[String, Any] =
      branch match {
        //        case LiftedProjectionBranch(subfield) =>
        case ObjectProjectionBranch(fields) =>
          val obj = new mutable.LinkedHashMap[String, Any]

          for (f <- fields)
            f match {
              case EntityProjectionNode(table, field, branch) =>
                obj(field) = build(branch)
              case PrimitiveProjectionNode(table, field, typ) =>
                obj(field) = row(md.tableColumnMap(table, field))
            }

          obj.toMap
      }

    build(branch)
  }

  abstract class ProjectionNode { val table: String; val field: String }
  case class PrimitiveProjectionNode(table: String,
                                     field: String,
                                     typ: EntityAttribute)
      extends ProjectionNode
  case class EntityProjectionNode(table: String,
                                  field: String,
                                  branch: ProjectionBranch)
      extends ProjectionNode

  abstract class ProjectionBranch
  case class ObjectProjectionBranch(fields: Seq[ProjectionNode])
      extends ProjectionBranch
  case class LiftedProjectionBranch(subfield: ProjectionNode)
      extends ProjectionBranch

}
