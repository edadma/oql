package xyz.hyperreal.oql

import scala.scalajs.js
import js.JSConverters._
import js.JSON
import js.annotation.{JSExport, JSExportTopLevel}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success

@JSExportTopLevel("OQL")
class OQL(erd: String) {

  private val model = new ERModel(erd)

  @JSExport("query")
  def jsQuery(sql: String, conn: Connection): js.Promise[js.Any] = query(sql, conn).map(toJS).toJSPromise

  def json(sql: String, conn: Connection) =
    query(sql, conn).map(value => JSON.stringify(toJS(value), null.asInstanceOf[js.Array[js.Any]], 2))

  def query(sql: String, conn: Connection): Future[List[Map[String, Any]]] = {
    val OQLQuery(resource, project, select, order, group, restrict) =
      OQLParser.parseQuery(sql)
    val entity = model.get(resource.name, resource.pos)
    val projectbuf = new ListBuffer[(String, String)]
    val joinbuf = new ListBuffer[(String, String, String, String, String)]
    val graph = branches(resource.name, entity, project, projectbuf, joinbuf, List(resource.name))

    executeQuery(resource.name, select, order, group, restrict, entity, projectbuf, joinbuf, graph, conn)
  }

  private def executeQuery(resource: String,
                           select: Option[ExpressionOQL],
                           order: Option[List[(ExpressionOQL, Boolean)]],
                           group: Option[List[VariableExpressionOQL]],
                           restrict: (Option[Int], Option[Int]),
                           entity: Entity,
                           projectbuf: ListBuffer[(String, String)],
                           joinbuf: ListBuffer[(String, String, String, String, String)],
                           graph: Seq[ProjectionNode],
                           conn: Connection): Future[List[Map[String, Any]]] = {
    val sql = new StringBuilder
    val projects: Seq[String] = projectbuf.toList map { case (e, f) => s"$e.$f" }

    sql append s"SELECT ${projects.head}${if (projects.tail nonEmpty) "," else ""}\n"
    sql append (projects.tail map ("       " ++ _) mkString ",\n")

    if (projects.tail nonEmpty)
      sql append '\n'

    sql append s"  FROM $resource\n"

    val where =
      if (select isDefined)
        expression(resource, entity, select.get, joinbuf)
      else
        null
    val groupby =
      if (group isDefined)
        group.get map (v => expression(resource, entity, v, joinbuf)) mkString ", "
      else
        null
    val orderby =
      if (order isDefined)
        order.get map {
          case (e, o) => s"(${expression(resource, entity, e, joinbuf)}) ${if (o) "ASC" else "DESC"}"
        } mkString ", "
      else
        null

    for ((lt, lf, rt, rta, rf) <- joinbuf.distinct)
      sql append s"  LEFT OUTER JOIN $rt AS $rta ON $lt.$lf = $rta.$rf\n"

    if (select isDefined)
      sql append s"  WHERE $where\n"

    if (group isDefined)
      sql append s"  GROUP BY $groupby\n"

    if (order isDefined)
      sql append s"  ORDER BY $orderby\n"

    print(sql)

    val projectmap = projectbuf.zipWithIndex.toMap

    conn
      .query(sql.toString)
      .rowSet
      .flatMap(result => {
        val list = result.toList
        val futurebuf = new ListBuffer[Future[List[Map[String, Any]]]]
        val futuremap =
          new mutable.HashMap[(ResultRow, EntityArrayJunctionProjectionNode), Future[List[Map[String, Any]]]]

        list foreach (futures(_, futurebuf, futuremap, projectmap, graph, conn))
        Future.sequence(futurebuf).map(_ => list map (build(_, projectmap, futuremap, graph, conn)))
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
          buf append s" ${op.toUpperCase} "
          expression(right)
        case PrefixExpressionOQL(op, expr) =>
          buf append s" ${op.toUpperCase}"
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
    @scala.annotation.tailrec
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
                val attrlist1 = attr.name :: attrlist // was column :: attrlist

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

//    if (entity.pk.isDefined && !attrs.exists(_._1 == entity.pk.get))
//      projectbuf += table -> entity.pk.get

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
      case (field, ObjectArrayJunctionEntityAttribute(entityType, attrEntity, junctionType, junction), project) =>
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

        EntityArrayJunctionProjectionNode(
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
//        EntityArrayJunctionProjectionNode(
//          field,
//          table,
//          entity.pk.get, // used to be attrEntity.pk.get
//          projectbuf,
//          subjoinbuf,
//          junctionType,
//          column,
//          junction,
//          branches(junctionType,
//                   junction,
//                   ProjectAttributesOQL(List(AttributeOQL(Ident(junctionAttr), project))),
//                   projectbuf,
//                   subjoinbuf,
//                   List(junctionType))
//        )
    }
  }

  private def futures(
      row: ResultRow,
      futurebuf: ListBuffer[Future[List[Map[String, Any]]]],
      futuremap: mutable.HashMap[(ResultRow, EntityArrayJunctionProjectionNode), Future[List[Map[String, Any]]]],
      projectmap: Map[(String, String), Int],
      branches: Seq[ProjectionNode],
      conn: Connection): Unit = {
    def futures(branches: Seq[ProjectionNode]): Unit = {
      branches foreach {
        case EntityProjectionNode(_, branches) => futures(branches)
        case PrimitiveProjectionNode(_, _, _)  =>
        case node @ EntityArrayJunctionProjectionNode(_,
                                                      tabpk,
                                                      colpk,
                                                      subprojectbuf,
                                                      subjoinbuf,
                                                      resource,
                                                      column,
                                                      entity,
                                                      branches) =>
          val future = executeQuery(
            resource,
            Some(EqualsExpressionOQL(resource, column, row(projectmap((tabpk, colpk))).toString)),
            None,
            None,
            (None, None),
            entity,
            subprojectbuf,
            subjoinbuf,
            branches,
            conn
          )

          futurebuf += future
          futuremap((row, node)) = future
      }
    }

    futures(branches)
  }

  private def build(
      row: ResultRow,
      projectmap: Map[(String, String), Int],
      futuremap: mutable.HashMap[(ResultRow, EntityArrayJunctionProjectionNode), Future[List[Map[String, Any]]]],
      branches: Seq[ProjectionNode],
      conn: Connection) = {
    def build(branches: Seq[ProjectionNode]): Map[String, Any] = {
      (branches map {
        case EntityProjectionNode(field, branches)    => field -> build(branches)
        case PrimitiveProjectionNode(table, field, _) => field -> row(projectmap((table, field)))
        case node @ EntityArrayJunctionProjectionNode(field, tabpk, colpk, _, _, _, _, _, branches) =>
          futuremap((row, node)).value match {
            case Some(Success(value)) => field -> (value map (m => m.head._2))
            case None                 => sys.error(s"failed to execute query: $field, $tabpk, $colpk, $branches")
          }
      }) toMap
    }

    build(branches)
  }

  abstract class ProjectionNode { val field: String }
  case class PrimitiveProjectionNode(table: String, field: String, typ: EntityAttribute) extends ProjectionNode
  case class EntityProjectionNode(field: String, branches: Seq[ProjectionNode]) extends ProjectionNode
  case class EntityArrayJunctionProjectionNode(field: String,
                                               tabpk: String,
                                               colpk: String,
                                               subprojectbuf: ListBuffer[(String, String)],
                                               subjoinbuf: ListBuffer[(String, String, String, String, String)],
                                               resource: String,
                                               column: String,
                                               entity: Entity,
                                               branches: Seq[ProjectionNode])
      extends ProjectionNode

}
