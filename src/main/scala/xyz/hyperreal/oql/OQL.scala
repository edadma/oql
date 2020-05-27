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
    val QueryOQL(resource, project, select, group, order, restrict) = OQLParser.parseQuery(sql)
    val entity = model.get(resource.name, resource.pos)
    val projectbuf = new ListBuffer[(Option[String], String, String)]
    val joinbuf = new ListBuffer[(String, String, String, String, String)]
    val graph = branches(resource.name, entity, project, projectbuf, joinbuf, List(resource.name))

    executeQuery(resource.name, select, group, order, restrict, entity, projectbuf, joinbuf, graph, conn)
  }

  private def executeQuery(resource: String,
                           select: Option[ExpressionOQL],
                           group: Option[List[VariableExpressionOQL]],
                           order: Option[List[(ExpressionOQL, Boolean)]],
                           restrict: (Option[Int], Option[Int]),
                           entity: Entity,
                           projectbuf: ListBuffer[(Option[String], String, String)],
                           joinbuf: ListBuffer[(String, String, String, String, String)],
                           graph: Seq[ProjectionNode],
                           conn: Connection): Future[List[Map[String, Any]]] = {
    val sql = new StringBuilder
    val projects: Seq[String] = projectbuf.toList map {
      case (None, e, f)    => s"$e.$f"
      case (Some(a), e, f) => s"$a($e.$f)"
    }

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

    //print(sql)

    val projectmap = projectbuf
      .map {
        case (None, t, f)    => (t, f)
        case (Some(a), t, f) => (t, s"${a}_$f")
      }
      .zipWithIndex
      .toMap

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
              if (tail != Nil)
                problem(attr.pos, s"'${attr.pos}' is a primitive type and so has no components")

              s"${attrlist mkString "$"}.$column"
            case Some(ObjectEntityAttribute(column, entityType, entity)) =>
              if (tail == Nil)
                problem(attr.pos, s"attribute '${attr.name}' has non-primitive data type")
              else {
                val attrlist1 = attr.name :: attrlist

                joinbuf += ((attrlist mkString "$", column, entityType, attrlist1 mkString "$", entity.pk.get))
                reference(entityType, entity, tail, attrlist1)
              }
            case Some(_: ObjectArrayEntityAttribute | _: ObjectArrayJunctionEntityAttribute) =>
              problem(attr.pos, s"attribute '${attr.name}' is an array type and may not be referenced")
          }
      }

    reference(entityname, entity, ids, List(entityname))
  }

  private def branches(entityname: String,
                       entity: Entity,
                       project: ProjectExpressionOQL,
                       projectbuf: ListBuffer[(Option[String], String, String)],
                       joinbuf: ListBuffer[(String, String, String, String, String)],
                       attrlist: List[String]): Seq[ProjectionNode] = {
    def attrType(attr: Ident) =
      entity.attributes get attr.name match {
        case None      => problem(attr.pos, s"unknown attribute: '${attr.name}'")
        case Some(typ) => typ
      }

    val attrs =
      if (project == ProjectAllOQL) {
        entity.attributes map { case (k, v) => (None, k, v, ProjectAllOQL, null) } toList
      } else {
        project.asInstanceOf[ProjectAttributesOQL].attrs map {
          case AggregateAttributeOQL(agg, attr) =>
            attrType(attr) match {
              case typ: PrimitiveEntityAttribute => (Some(agg.name), attr.name, typ, ProjectAllOQL, null)
              case _                             => problem(agg.pos, s"can't apply an aggregate function to a non-primitive attribute")
            }
          case query @ QueryOQL(attr, project, None, None, None, (None, None)) =>
            (None, attr.name, attrType(attr), project, query)
          case query @ QueryOQL(source, project, _, _, _, _) =>
            attrType(source) match {
              case typ: ObjectArrayJunctionEntityAttribute =>
                (None, source.name, typ, project, query)
              case _ =>
                problem(source.pos, s"'${source.name}' is not an array type attribute")
            }
        }
      }
    val table = attrlist mkString "$"

    if (attrs.exists {
          case (_, _, _: ObjectArrayJunctionEntityAttribute, _, _) => true
          case _                                                   => false
        } && entity.pk.isDefined && !attrs.exists(_._2 == entity.pk.get))
      projectbuf += ((None, table, entity.pk.get))

    attrs map {
      case (agg, field, attr: PrimitiveEntityAttribute, _, _) =>
        projectbuf += ((agg, table, field))
        PrimitiveProjectionNode(agg.map(a => s"${a}_$field").getOrElse(field), table, field, attr)
      case (_, field, attr: ObjectEntityAttribute, project, _) =>
        if (attr.entity.pk isEmpty)
          problem(null, s"entity '${attr.entityType}' is referenced as a type but has no primary key")

        val attrlist1 = field :: attrlist

        joinbuf += ((table, attr.column, attr.entityType, attrlist1 mkString "$", attr.entity.pk.get))
        EntityProjectionNode(field, branches(attr.entityType, attr.entity, project, projectbuf, joinbuf, attrlist1))
      case (_,
            field,
            ObjectArrayJunctionEntityAttribute(entityType, attrEntity, junctionType, junction),
            project,
            query) =>
        val projectbuf = new ListBuffer[(Option[String], String, String)]
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
          branches(
            junctionType,
            junction,
            ProjectAttributesOQL(
              List(
                QueryOQL(
                  Ident(junctionAttr),
                  project,
                  None,
                  None,
                  None,
                  (None, None)
                ))),
            projectbuf,
            subjoinbuf,
            List(junctionType)
          ),
          query
        )
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
        case _: PrimitiveProjectionNode        =>
        case node @ EntityArrayJunctionProjectionNode(_,
                                                      tabpk,
                                                      colpk,
                                                      subprojectbuf,
                                                      subjoinbuf,
                                                      resource,
                                                      column,
                                                      entity,
                                                      branches,
                                                      query) =>
          val pkwhere = EqualsExpressionOQL(resource, column, row(projectmap((tabpk, colpk))).toString)
          println(query)
          val future = executeQuery(
            resource,
            Some(query.select.fold(pkwhere.asInstanceOf[ExpressionOQL])(c => InfixExpressionOQL(pkwhere, "AND", c))),
            query.group,
            query.order,
            query.restrict,
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
        case EntityProjectionNode(field, branches) => field -> build(branches)
        case PrimitiveProjectionNode(name, table, field, _) =>
          name -> row(projectmap((table, name))) // used to be row(projectmap((table, field)))
        case node @ EntityArrayJunctionProjectionNode(field, tabpk, colpk, _, _, _, _, _, branches, _) =>
          futuremap((row, node)).value match {
            case Some(Success(value)) => field -> (value map (m => m.head._2))
            case None                 => sys.error(s"failed to execute query: $field, $tabpk, $colpk, $branches")
          }
      }) toMap
    }

    build(branches)
  }

  abstract class ProjectionNode { val field: String }
  case class PrimitiveProjectionNode(name: String, table: String, field: String, typ: EntityAttribute)
      extends ProjectionNode
  case class EntityProjectionNode(field: String, branches: Seq[ProjectionNode]) extends ProjectionNode
  case class EntityArrayJunctionProjectionNode(field: String,
                                               tabpk: String,
                                               colpk: String,
                                               subprojectbuf: ListBuffer[(Option[String], String, String)],
                                               subjoinbuf: ListBuffer[(String, String, String, String, String)],
                                               resource: String,
                                               column: String,
                                               entity: Entity,
                                               branches: Seq[ProjectionNode],
                                               query: QueryOQL)
      extends ProjectionNode
  case class EntityArrayProjectionNode(field: String,
                                       tabpk: String,
                                       colpk: String,
                                       subprojectbuf: ListBuffer[(Option[String], String, String)],
                                       subjoinbuf: ListBuffer[(String, String, String, String, String)],
                                       resource: String,
                                       column: String,
                                       entity: Entity,
                                       branches: Seq[ProjectionNode],
                                       query: QueryOQL)
      extends ProjectionNode

}
