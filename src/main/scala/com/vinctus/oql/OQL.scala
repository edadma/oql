package com.vinctus.oql

import scala.scalajs.js
import js.JSON
import js.annotation.{JSExport, JSExportTopLevel}
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

@JSExportTopLevel("OQL")
class OQL(erd: String) {

  private val model = new ERModel(erd)

  @JSExport
  def queryBuilder() = new QueryBuilder(this, QueryOQL(null, ProjectAllOQL, None, None, None, None, None))

  @JSExport("query")
  def jsQuery(sql: String, conn: Connection): js.Promise[js.Any] = toPromise(query(sql, conn))

  def jsQuery(q: QueryOQL, conn: Connection): js.Promise[js.Any] = toPromise(query(q, conn))

  def json(oql: String, conn: Connection): Future[String] =
    query(oql, conn).map(value => JSON.stringify(toJS(value), null.asInstanceOf[js.Array[js.Any]], 2))

  def query(oql: String, conn: Connection): Future[List[ListMap[String, Any]]] = query(OQLParser.parseQuery(oql), conn)

  def query(q: QueryOQL, conn: Connection): Future[List[ListMap[String, Any]]] = {
    val QueryOQL(resource, project, select, group, order, limit, offset) = q
    val entity = model.get(resource.name, resource.pos)
    val projectbuf = new ListBuffer[(Option[String], String, String)]
    val joinbuf = new ListBuffer[(String, String, String, String, String)]
    val graph = branches(resource.name, entity, project, projectbuf, joinbuf, List(entity.table), Nil)

    executeQuery(resource.name, select, group, order, limit, offset, entity, projectbuf, joinbuf, graph, conn)
  }

  private def executeQuery(resource: String,
                           select: Option[ExpressionOQL],
                           group: Option[List[VariableExpressionOQL]],
                           order: Option[List[(ExpressionOQL, Boolean)]],
                           limit: Option[Int],
                           offset: Option[Int],
                           entity: Entity,
                           projectbuf: ListBuffer[(Option[String], String, String)],
                           joinbuf: ListBuffer[(String, String, String, String, String)],
                           graph: Seq[ProjectionNode],
                           conn: Connection): Future[List[ListMap[String, Any]]] = {
    val sql = new StringBuilder
    val projects: Seq[String] = projectbuf.toList map {
      case (None, e, f)    => s"$e.$f"
      case (Some(a), e, f) => s"$a($e.$f)"
    }

    sql append s"SELECT ${projects.head}${if (projects.tail nonEmpty) "," else ""}\n"
    sql append (projects.tail map ("       " ++ _) mkString ",\n")

    if (projects.tail nonEmpty)
      sql append '\n'

    sql append s"  FROM ${entity.table}\n"

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
      sql append s"    LEFT OUTER JOIN $rt AS $rta ON $lt.$lf = $rta.$rf\n"

    if (select isDefined)
      sql append s"  WHERE $where\n"

    if (group isDefined)
      sql append s"  GROUP BY $groupby\n"

    if (order isDefined)
      sql append s"  ORDER BY $orderby\n"

    (limit, offset) match {
      case (None, None)       =>
      case (Some(l), None)    => sql append s"  LIMIT $l"
      case (Some(l), Some(o)) => sql append s"  LIMIT $l OFFSET $o"
      case (None, Some(o))    => sql append s"  OFFSET $o"
    }

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
        val futurebuf = new ListBuffer[Future[List[ListMap[String, Any]]]]
        val futuremap =
          new mutable.HashMap[(ResultRow, ProjectionNode), Future[List[ListMap[String, Any]]]]

        list foreach (futures(_, futurebuf, futuremap, projectmap, graph, conn))
        Future.sequence(futurebuf).map(_ => list map (build(_, projectmap, futuremap, graph, conn)))
      })
  }

  private def expression(entityname: String,
                         entity: Entity,
                         expr: ExpressionOQL,
                         joinbuf: ListBuffer[(String, String, String, String, String)]) = {
    val buf = new StringBuilder

    def expressions(list: List[ExpressionOQL]): Unit = {
      buf += '('
      expression(list.head)

      for (e <- list.tail) {
        buf ++= ", "
        expression(e)
      }

      buf += ')'
    }

    def expression(expr: ExpressionOQL): Unit =
      expr match {
        case EqualsExpressionOQL(table, column, value) => buf append s"$table.$column = $value"
        case InfixExpressionOQL(left, op, right) =>
          expression(left)
          buf append s" ${op.toUpperCase} "
          expression(right)
        case PrefixExpressionOQL(op, expr) =>
          buf append s"${op.toUpperCase} "
          expression(expr)
        case PostfixExpressionOQL(expr, op) =>
          expression(expr)
          buf append s" ${op.toUpperCase}"
        case InExpressionOQL(expr, op, list) =>
          expression(expr)
          buf ++= s" $op "
          expressions(list)
        case FloatLiteralOQL(n)   => buf append n
        case IntegerLiteralOQL(n) => buf append n
        case StringLiteralOQL(s)  => buf append s"'$s'"
        case GroupedExpressionOQL(expr) =>
          buf += '('
          expression(expr)
          buf += ')'
        case ApplyExpressionOQL(func, args) =>
          buf ++= func.name
          expressions(args)
        case VariableExpressionOQL(ids)  => buf append reference(entityname, entity, ids, ref = false, joinbuf)
        case ReferenceExpressionOQL(ids) => buf append reference(entityname, entity, ids, ref = true, joinbuf)
        case CaseExpressionOQL(whens, els) =>
          buf ++= "CASE"

          for ((c, r) <- whens) {
            buf ++= " WHEN "
            expression(c)
            buf ++= " THEN "
            expression(r)
          }

          els foreach (e => {
            buf ++= " ELSE "
            expression(e)
          })

          buf ++= " END"
      }

    expression(expr)
    buf.toString
  }

  private def reference(
      entityname: String,
      entity: Entity,
      ids: List[Ident],
      ref: Boolean,
      joinbuf: ListBuffer[(String, String, String, String, String)],
  ) = {
    @scala.annotation.tailrec
    def reference(entityname: String, entity: Entity, ids: List[Ident], attrlist: List[String]): String =
      ids match {
        case Nil => sys.error("reference: problem")
        case attr :: tail =>
          entity.attributes get attr.name match {
            case None =>
              problem(attr.pos, s"resource '$entityname' doesn't have an attribute '${attr.name}'")
            case Some(PrimitiveEntityAttribute(column, _)) =>
              if (tail != Nil)
                problem(attr.pos, s"'${attr.pos}' is a primitive type and so has no components")

              s"${attrlist mkString "$"}.$column"
            case Some(ObjectEntityAttribute(column, entityType, entity)) =>
              if (tail == Nil) {
                if (ref)
                  s"${attrlist mkString "$"}.$column"
                else
                  problem(attr.pos, s"attribute '${attr.name}' has non-primitive data type")
              } else {
                val attrlist1 = entity.table :: attrlist

                joinbuf += ((attrlist mkString "$", column, entity.table, attrlist1 mkString "$", entity.pk.get))
                reference(entityType, entity, tail, attrlist1)
              }
            case Some(_: ObjectArrayEntityAttribute | _: ObjectArrayJunctionEntityAttribute) =>
              problem(attr.pos, s"attribute '${attr.name}' is an array type and may not be referenced")
          }
      }

    reference(entityname, entity, ids, List(entity.table))
  }

  private def entityNode(field: String, attr: EntityAttribute, circlist: List[(String, EntityAttribute)])(
      node: List[(String, EntityAttribute)] => ProjectionNode) =
    circlist.find { case (_, a) => a == attr } match {
      case None         => node((field, attr) :: circlist)
      case Some((f, a)) => sys.error(s"circularity from attribute '$f' of type '${a.typ}'")
    }

  private def branches(entityname: String,
                       entity: Entity,
                       project: ProjectExpressionOQL,
                       projectbuf: ListBuffer[(Option[String], String, String)],
                       joinbuf: ListBuffer[(String, String, String, String, String)],
                       attrlist: List[String],
                       circlist: List[(String, EntityAttribute)]): Seq[ProjectionNode] = {
    def attrType(attr: Ident) =
      entity.attributes get attr.name match {
        case None      => problem(attr.pos, s"entity '$entityname' does not have attribute: '${attr.name}'")
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
          case query @ QueryOQL(attr, project, None, None, None, None, None) =>
            (None, attr.name, attrType(attr), project, query)
          case query @ QueryOQL(source, project, _, _, _, _, _) =>
            attrType(source) match {
              case typ @ (_: ObjectArrayJunctionEntityAttribute | _: ObjectArrayEntityAttribute) =>
                (None, source.name, typ, project, query)
              case _ =>
                problem(source.pos, s"'${source.name}' is not an array type attribute")
            }
        }
      }
    val table = attrlist mkString "$"

    // add the primary key to projectbuf if an array type attribute is being projected
    if (attrs.exists {
          case (_, _, _: ObjectArrayJunctionEntityAttribute | _: ObjectArrayEntityAttribute, _, _) => true
          case _                                                                                   => false
        } && entity.pk.isDefined && !attrs.exists(_._2 == entity.pk.get))
      projectbuf += ((None, table, entity.pk.get))

    attrs map {
      case (agg, field, attr: PrimitiveEntityAttribute, _, _) =>
        projectbuf += ((agg, table, attr.column))
        PrimitiveProjectionNode(agg.map(a => s"${a}_$field").getOrElse(field),
                                agg.map(a => s"${a}_${attr.column}").getOrElse(attr.column),
                                table,
                                field,
                                attr)
      case (_, field, attr: ObjectEntityAttribute, project, _) =>
        if (attr.entity.pk isEmpty)
          problem(null, s"entity '${attr.typ}' is referenced as a type but has no primary key")

        val attrlist1 = attr.entity.table :: attrlist

        joinbuf += ((table, attr.column, attr.entity.table, attrlist1 mkString "$", attr.entity.pk.get))

        entityNode(field, attr, circlist)(c =>
          EntityProjectionNode(field, branches(attr.typ, attr.entity, project, projectbuf, joinbuf, attrlist1, c)))
      case (_,
            field,
            attr @ ObjectArrayJunctionEntityAttribute(entityType, attrEntity, junctionType, junction),
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

        entityNode(field, attr, circlist)(
          c =>
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
                      None,
                      None
                    ))),
                projectbuf,
                subjoinbuf,
                List(junction.table),
                c
              ),
              query
          ))
      case (_, field, attr @ ObjectArrayEntityAttribute(entityType, attrEntity), project, query) =>
        val projectbuf = new ListBuffer[(Option[String], String, String)]
        val subjoinbuf = new ListBuffer[(String, String, String, String, String)]
        val es = attrEntity.attributes.toList.filter(
          a =>
            a._2
              .isInstanceOf[ObjectEntityAttribute] && a._2.asInstanceOf[ObjectEntityAttribute].entity == entity)
        val column =
          es.length match {
            case 0 => problem(null, s"'$entityType' does not contain an attribute of type '$entityname'")
            case 1 => es.head._2.asInstanceOf[ObjectEntityAttribute].column
            case _ => problem(null, s"'$entityType' contains more than one attribute of type '$entityname'")
          }

        entityNode(field, attr, circlist)(
          c =>
            EntityArrayProjectionNode(
              field,
              table,
              entity.pk.get,
              projectbuf,
              subjoinbuf,
              entityType,
              column,
              attrEntity,
              branches(
                entityType,
                attrEntity,
                project,
                projectbuf,
                subjoinbuf,
                List(attrEntity.table),
                c
              ),
              query
          ))
    }
  }

  private def render(a: Any) =
    a match {
      case s: String => s"'$s'"
      case _         => a.toString
    }

  private def futures(row: ResultRow,
                      futurebuf: ListBuffer[Future[List[ListMap[String, Any]]]],
                      futuremap: mutable.HashMap[(ResultRow, ProjectionNode), Future[List[ListMap[String, Any]]]],
                      projectmap: Map[(String, String), Int],
                      nodes: Seq[ProjectionNode],
                      conn: Connection): Unit = {
    def futures(nodes: Seq[ProjectionNode]): Unit = {
      nodes foreach {
        case _: PrimitiveProjectionNode     =>
        case EntityProjectionNode(_, nodes) => futures(nodes)
        case node @ EntityArrayJunctionProjectionNode(_,
                                                      tabpk,
                                                      colpk,
                                                      subprojectbuf,
                                                      subjoinbuf,
                                                      resource,
                                                      column,
                                                      entity,
                                                      nodes,
                                                      query) =>
          val pkwhere = EqualsExpressionOQL(entity.table, column, render(row(projectmap((tabpk, colpk)))))
          val future = executeQuery(
            resource,
            Some(query.select.fold(pkwhere.asInstanceOf[ExpressionOQL])(c => InfixExpressionOQL(pkwhere, "AND", c))),
            query.group,
            query.order,
            query.limit,
            query.offset,
            entity,
            subprojectbuf,
            subjoinbuf,
            nodes,
            conn
          )

          futurebuf += future
          futuremap((row, node)) = future
        case node @ EntityArrayProjectionNode(_,
                                              tabpk,
                                              colpk,
                                              subprojectbuf,
                                              subjoinbuf,
                                              resource,
                                              column,
                                              entity,
                                              nodes,
                                              query) =>
          val pkwhere = EqualsExpressionOQL(entity.table, column, render(row(projectmap((tabpk, colpk)))))
          val future = executeQuery(
            resource,
            Some(query.select.fold(pkwhere.asInstanceOf[ExpressionOQL])(c => InfixExpressionOQL(pkwhere, "AND", c))),
            query.group,
            query.order,
            query.limit,
            query.offset,
            entity,
            subprojectbuf,
            subjoinbuf,
            nodes,
            conn
          )

          futurebuf += future
          futuremap((row, node)) = future
      }
    }

    futures(nodes)
  }

  private def build(row: ResultRow,
                    projectmap: Map[(String, String), Int],
                    futuremap: mutable.HashMap[(ResultRow, ProjectionNode), Future[List[ListMap[String, Any]]]],
                    branches: Seq[ProjectionNode],
                    conn: Connection) = {
    def build(branches: Seq[ProjectionNode]): ListMap[String, Any] = {
      (branches map {
        case EntityProjectionNode(field, branches) => field -> build(branches)
        case PrimitiveProjectionNode(prop, column, table, _, _) =>
          prop -> row(projectmap((table, column))) // used to be row(projectmap((table, name)))
        case node @ EntityArrayJunctionProjectionNode(field, tabpk, colpk, _, _, _, _, _, branches, _) =>
          futuremap((row, node)).value match {
            case Some(Success(value)) => field -> (value map (m => m.head._2))
            case a                    => sys.error(s"failed to execute query: $a")
          }
        case node @ EntityArrayProjectionNode(field, tabpk, colpk, _, _, _, _, _, branches, _) =>
          futuremap((row, node)).value match {
            case Some(Success(value)) => field -> value
            case a                    => sys.error(s"failed to execute query: $a")
          }
      }).to(ListMap)
    }

    build(branches)
  }

  abstract class ProjectionNode { val field: String }
  case class PrimitiveProjectionNode(name: String, column: String, table: String, field: String, attr: EntityAttribute)
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