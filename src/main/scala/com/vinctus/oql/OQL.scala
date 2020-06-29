package com.vinctus.oql

import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}
import scala.collection.immutable.ListMap
import scala.collection.mutable.ListBuffer
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success
import scala.concurrent.ExecutionContext.Implicits.global

@JSExportTopLevel("OQL")
class OQL(private[oql] val conn: Connection, erd: String) extends Dynamic {

  private val model = new ERModel(erd)

  @JSExport
  def entity(resource: String): Resource =
    model get resource match {
      case None         => sys.error(s"resource '$resource' not found")
      case Some(entity) => new Resource(this, resource, entity)
    }

  def selectDynamic(resource: String): Resource = entity(resource)

  @JSExport
  def queryBuilder() =
    new QueryBuilder(this, QueryOQL(null, ProjectAllOQL(), None, None, None, None, None))

  @JSExport("queryOne")
  def jsQueryOne(oql: String): js.Promise[js.Any] = jsQueryOne(OQLParser.parseQuery(oql))

  def jsQueryOne(q: QueryOQL): js.Promise[js.Any] = toPromiseOne(queryOne(q))

  @JSExport("queryMany")
  def jsQueryMany(oql: String): js.Promise[js.Any] = jsQueryMany(OQLParser.parseQuery(oql))

  def jsQueryMany(q: QueryOQL): js.Promise[js.Any] = toPromise(queryMany(q))

  def json(oql: String): Future[String] = toJSON(queryMany(oql))

  def queryOne(oql: String): Future[Option[ListMap[String, Any]]] =
    queryOne(OQLParser.parseQuery(oql))

  def queryOne(oql: QueryOQL): Future[Option[ListMap[String, Any]]] =
    queryMany(oql) map {
      case Nil       => None
      case List(row) => Some(row)
      case _         => sys.error("queryOne: more than one was found")
    }

  def queryMany(oql: String, parameters: Map[String, String] = null): Future[List[ListMap[String, Any]]] =
    queryMany(OQLParser.parseQuery(oql))

  def queryMany(q: QueryOQL): Future[List[ListMap[String, Any]]] = {
    val QueryOQL(resource, project, select, group, order, limit, offset) = q
    val entity = model.get(resource.name, resource.pos)
    val projectbuf = new ListBuffer[(Option[String], String, String)]
    val joinbuf = new ListBuffer[(String, String, String, String, String)]
    val graph = branches(resource.name, entity, project, group.isEmpty, projectbuf, joinbuf, List(entity.table), Nil)

    executeQuery(resource.name, select, group, order, limit, offset, None, entity, projectbuf, joinbuf, graph)
  }

  @JSExport("findOne")
  def jsFindOne(resource: String, id: js.Any): js.Promise[js.Any] =
    toPromiseOne(findOne(resource, id))

  def findOne(resource: String, id: Any): Future[Option[ListMap[String, Any]]] = {
    model.get(resource) match {
      case Some(entity) =>
        entity.pk match {
          case None => sys.error(s"findOne: resource '$resource' doesn't have a declared primary key")
          case Some(pk) =>
            queryOne(
              QueryOQL(
                Ident(resource),
                ProjectAllOQL(),
                Some(
                  EqualsExpressionOQL(entity.table,
                                      entity.attributes(pk).asInstanceOf[PrimitiveEntityAttribute].column,
                                      s"'$id'")),
                None,
                None,
                None,
                None
              )
            )
        }
      case None => sys.error(s"findOne: unknown resource '$resource'")
    }
  }

  private def executeQuery(resource: String,
                           select: Option[ExpressionOQL],
                           group: Option[List[VariableExpressionOQL]],
                           order: Option[List[(ExpressionOQL, String)]],
                           limit: Option[Int],
                           offset: Option[Int],
                           entityType: Option[String],
                           entity: Entity,
                           projectbuf: ListBuffer[(Option[String], String, String)],
                           joinbuf: ListBuffer[(String, String, String, String, String)],
                           graph: Seq[ProjectionNode]): Future[List[ListMap[String, Any]]] = {
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
        expression(resource, entity, entityType, select.get, joinbuf)
      else
        null
    val groupby =
      if (group isDefined)
        group.get map (v => expression(resource, entity, entityType, v, joinbuf)) mkString ", "
      else
        null
    val orderby =
      if (order isDefined)
        order.get map {
          case (e, o) => s"(${expression(resource, entity, entityType, e, joinbuf)}) $o"
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
        val futuremap = new mutable.HashMap[(ResultRow, Int), Future[List[ListMap[String, Any]]]]

        list foreach (futures(_, futurebuf, futuremap, projectmap, graph))
        Future.sequence(futurebuf).map(_ => list map (build(_, projectmap, futuremap, graph)))
      })
  }

  private def expression(entityname: String,
                         entity: Entity,
                         entityType: Option[String],
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
        case BetweenExpressionOQL(expr, op, lower, upper) =>
          expression(expr)
          buf ++= s" $op "
          expression(lower)
          buf ++= " AND "
          expression(upper)
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
        case VariableExpressionOQL(ids) =>
          buf append reference(entityname, entity, entityType, ids, ref = false, joinbuf)
        case ReferenceExpressionOQL(ids) =>
          buf append reference(entityname, entity, entityType, ids, ref = true, joinbuf)
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
      entityType: Option[String],
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
            case Some(LiteralEntityAttribute(_)) => problem(attr.pos, "literals are not yet supported here")
            case Some(PrimitiveEntityAttribute(column, _, _)) =>
              if (tail != Nil)
                problem(attr.pos, s"'${attr.pos}' is a primitive type and so has no components")

              s"${attrlist mkString "$"}.$column"
            case Some(ObjectEntityAttribute(column, entityType, entity, _)) =>
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
            case Some(
                _: ObjectArrayEntityAttribute | _: ObjectArrayJunctionEntityAttribute |
                _: ObjectOneEntityAttribute) => // todo: figure out how to add support for ObjectOneEntityAttribute here
              problem(attr.pos, s"attribute '${attr.name}' is an array type and may not be referenced")
          }
      }

    reference(entityname, entity, if (entityType isDefined) Ident(entityType.get) :: ids else ids, List(entity.table))
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
                       fk: Boolean,
                       projectbuf: ListBuffer[(Option[String], String, String)],
                       joinbuf: ListBuffer[(String, String, String, String, String)],
                       attrlist: List[String],
                       circlist: List[(String, EntityAttribute)]): Seq[ProjectionNode] = {
    def attrType(attr: Ident) =
      entity.attributes get attr.name match {
        case None      => problem(attr.pos, s"entity '$entityname' does not have attribute: '${attr.name}'")
        case Some(typ) => typ
      }

    project match {
      case ProjectAttributesOQL(attrs) =>
        attrs find
          (_.isInstanceOf[LiftedAttribute]) match {
          case Some(LiftedAttribute(q)) =>
            if (attrs.length > 1)
              problem(q.source.pos, s"lifted attribute '${q.source.name}' must be the sole attribute being projected")

            branches(entityname, entity, ProjectAttributesOQL(List(q)), fk, projectbuf, joinbuf, attrlist, circlist) match {
              case Seq(EntityProjectionNode(_, fk, bs)) =>
                return List(LiftedProjectionNode(fk, bs))
              case _ => problem(q.source.pos, s"can only lift an object attribute")
            }
          case None    =>
          case Some(_) => sys.error("problem")
        }
      case _ =>
    }

    val attrs =
      project match {
        case _: ProjectAllOQL =>
          entity.attributes filter {
            case (_,
                  _: ObjectArrayJunctionEntityAttribute | _: ObjectArrayEntityAttribute |
                  _: ObjectOneEntityAttribute) =>
              false
            case _            => true
          } map { case (k, v) => (None, k, v, ProjectAllOQL(), null, false) } toList
        case ProjectAttributesOQL(attrs) =>
          var projectall = false
          val aggset = new mutable.HashSet[AggregateAttributeOQL]
          val propset = attrs flatMap {
            case QueryOQL(id, _, _, _, _, _, _) => List(id.name)
            case ReferenceAttributeOQL(attr)    => List(attr.name)
            case _                              => Nil
          } toSet
          val negpropmap = attrs flatMap {
            case NegativeAttribute(id) => List(id.name -> id.pos)
            case _                     => Nil
          } toMap

          negpropmap.keySet.intersect(propset).toList match {
            case Nil     =>
            case id :: _ => problem(negpropmap(id), "can't negate an attribute that was explicitly given")
          }

          val propidset = new mutable.HashSet[Ident]

          val res =
            attrs flatMap {
              case ProjectAllOQL(pos) =>
                if (projectall)
                  problem(pos, "only one * can be used")
                else
                  projectall = true

                entity.attributes filter {
                  case (_,
                        _: ObjectArrayJunctionEntityAttribute | _: ObjectArrayEntityAttribute |
                        _: ObjectOneEntityAttribute) =>
                    false
                  case (k, _)       => !propset(k) && !negpropmap.contains(k)
                } map { case (k, v) => (None, k, v, ProjectAllOQL(), null, false) } toList
              case _: NegativeAttribute                           => Nil
              case ReferenceAttributeOQL(attr) if propidset(attr) => problem(attr.pos, "duplicate property")
              case ReferenceAttributeOQL(attr) =>
                propidset += attr

                attrType(attr) match {
                  case _: ObjectEntityAttribute => List((None, attr.name, attrType(attr), null, null, true))
                  case _                        => problem(attr.pos, s"can only apply a reference operator to an object attribute")
                }
              case a @ AggregateAttributeOQL(agg, attr) =>
                if (aggset(a))
                  problem(agg.pos, "aggregate function duplicated")

                aggset += a

                attrType(attr) match {
                  case typ: PrimitiveEntityAttribute =>
                    List((Some(agg.name), attr.name, typ, ProjectAllOQL(), null, false))
                  case _ => problem(agg.pos, s"can only apply an aggregate function to a primitive attribute")
                }
              case QueryOQL(id, _, _, _, _, _, _) if propidset(id) => problem(id.pos, "duplicate property")
              case query @ QueryOQL(attr, project, None, None, None, None, None) =>
                propidset += attr
                List((None, attr.name, attrType(attr), project, query, false))
              case query @ QueryOQL(source, project, _, _, _, _, _) =>
                propidset += source

                attrType(source) match {
                  case typ @ (_: ObjectArrayJunctionEntityAttribute | _: ObjectArrayEntityAttribute) =>
                    List((None, source.name, typ, project, query, false))
                  case _ =>
                    problem(source.pos, s"'${source.name}' is not an array type attribute")
                }
            }

          if (!projectall && negpropmap.nonEmpty)
            problem(negpropmap.head._2, "can't use attribute negation without a wildcard (i.e. '*')")

          res
      }
    val table = attrlist mkString "$"

    // add the primary key to projectbuf if an array type attribute is being projected
    if (attrs.exists {
          case (_, _, _: ObjectArrayJunctionEntityAttribute | _: ObjectArrayEntityAttribute, _, _, _) => true
          case _                                                                                      => false
        } && entity.pk.isDefined && !attrs.exists(_._2 == entity.pk.get))
      projectbuf += ((None, table, entity.pk.get))

    attrs map {
      case (_, field, attr: LiteralEntityAttribute, _, _, _) => LiteralProjectionNode(field, attr.value)
      case (agg, field, attr: PrimitiveEntityAttribute, _, _, _) =>
        projectbuf += ((agg, table, attr.column))
        PrimitiveProjectionNode(agg.map(a => s"${a}_$field").getOrElse(field),
                                agg.map(a => s"${a}_${attr.column}").getOrElse(attr.column),
                                table,
                                field,
                                attr)
      case (agg, field, attr: ObjectEntityAttribute, _, _, true) =>
        projectbuf += ((agg, table, attr.column))
        PrimitiveProjectionNode(agg.map(a => s"${a}_$field").getOrElse(field),
                                agg.map(a => s"${a}_${attr.column}").getOrElse(attr.column),
                                table,
                                field,
                                attr)
      case (_, field, attr: ObjectEntityAttribute, project, _, false) =>
        if (attr.entity.pk isEmpty)
          problem(null, s"entity '${attr.typ}' is referenced as a type but has no primary key")

        val attrlist1 = attr.entity.table :: attrlist

        joinbuf += ((table, attr.column, attr.entity.table, attrlist1 mkString "$", attr.entity.pk.get))

        if (fk)
          projectbuf += ((None, table, attr.column))

        entityNode(field, attr, circlist)(
          c =>
            EntityProjectionNode(field,
                                 if (fk) Some((table, attr.column)) else None,
                                 branches(attr.typ, attr.entity, project, fk, projectbuf, joinbuf, attrlist1, c)))
      case (_,
            field,
            attr @ ObjectArrayJunctionEntityAttribute(entityType, attrEntity, junctionType, junction),
            project,
            query,
            _) =>
        val projectbuf = new ListBuffer[(Option[String], String, String)]
        val subjoinbuf = new ListBuffer[(String, String, String, String, String)]
        val ts = junction.attributes.toList.filter(
          a =>
            a._2
              .isInstanceOf[ObjectEntityAttribute] && a._2
              .asInstanceOf[ObjectEntityAttribute]
              .entity == attrEntity)
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
              entityType,
              field,
              table,
              entity.pk.get,
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
                query.group.isEmpty,
                projectbuf,
                subjoinbuf,
                List(junction.table),
                c
              ),
              query
          ))
      case (_, field, attr @ ObjectArrayEntityAttribute(entityType, attrEntity), project, query, _) =>
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
                query.group.isEmpty,
                projectbuf,
                subjoinbuf,
                List(attrEntity.table),
                c
              ),
              query
          ))
      case (_, field, attr @ ObjectOneEntityAttribute(entityType, attrEntity, _), project, query, _) =>
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
            EntityOneProjectionNode(
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
                query.group.isEmpty,
                projectbuf,
                subjoinbuf,
                List(attrEntity.table),
                c
              ),
              query
          ))
    }
  }

  private def futures(row: ResultRow,
                      futurebuf: ListBuffer[Future[List[ListMap[String, Any]]]],
                      futuremap: mutable.HashMap[(ResultRow, Int), Future[List[ListMap[String, Any]]]],
                      projectmap: Map[(String, String), Int],
                      nodes: Seq[ProjectionNode]): Unit = {
    def futures(nodes: Seq[ProjectionNode]): Unit = {
      nodes foreach {
        case _: PrimitiveProjectionNode | _: LiteralProjectionNode =>
        case LiftedProjectionNode(_, nodes)                        => futures(nodes)
        case EntityProjectionNode(_, _, nodes)                     => futures(nodes)
        case node @ EntityArrayJunctionProjectionNode(entityType,
                                                      _,
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
            Some(entityType),
            entity,
            subprojectbuf,
            subjoinbuf,
            nodes
          )

          futurebuf += future
          futuremap((row, node.serial)) = future
        case node @ EntityOneProjectionNode(_,
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
            None,
            entity,
            subprojectbuf,
            subjoinbuf,
            nodes
          )

          futurebuf += future
          futuremap((row, node.serial)) = future
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
            None,
            entity,
            subprojectbuf,
            subjoinbuf,
            nodes
          )

          futurebuf += future
          futuremap((row, node.serial)) = future
      }
    }

    futures(nodes)
  }

  private def build(row: ResultRow,
                    projectmap: Map[(String, String), Int],
                    futuremap: mutable.HashMap[(ResultRow, Int), Future[List[ListMap[String, Any]]]],
                    branches: Seq[ProjectionNode]) = {
    def build(branches: Seq[ProjectionNode]): ListMap[String, Any] =
      branches match {
        case Seq(LiftedProjectionNode(Some(fk), branches)) => if (row(projectmap(fk)) == null) null else build(branches)
        case Seq(LiftedProjectionNode(None, branches))     => build(branches)
        case _ =>
          (branches map {
            case EntityProjectionNode(field, Some(fk), branches) =>
              field -> (if (row(projectmap(fk)) == null) null else build(branches))
            case EntityProjectionNode(field, None, branches)        => field -> build(branches)
            case LiteralProjectionNode(field, value)                => field -> value
            case PrimitiveProjectionNode(prop, column, table, _, _) => prop -> row(projectmap((table, column)))
            case node @ EntityArrayJunctionProjectionNode(_, field, _, _, _, _, _, _, _, _, _) =>
              futuremap((row, node.serial)).value match {
                case Some(Success(list)) => field -> (list map (m => m.head._2))
                case a                   => sys.error(s"failed to execute query: $a")
              }
            case node @ EntityArrayProjectionNode(field, _, _, _, _, _, _, _, _, _) =>
              futuremap((row, node.serial)).value match {
                case Some(Success(list)) => field -> list
                case a                   => sys.error(s"failed to execute query: $a")
              }
            case node @ EntityOneProjectionNode(field, _, _, _, _, _, _, _, _, _) =>
              futuremap((row, node.serial)).value match {
                case Some(Success(Nil))        => field -> null
                case Some(Success(List(item))) => field -> item
                case Some(Success(_))          => sys.error(s"not one-to-one: $field")
                case a                         => sys.error(s"failed to execute query: $a")
              }
          }).to(ListMap)
      }

    build(branches)
  }

  private object ProjectionNode { private var serial = 0 }
  private abstract class ProjectionNode {
    //val field: String
    val serial: Int = ProjectionNode.serial

    ProjectionNode.serial += 1
  }
  private case class LiteralProjectionNode(field: String, value: Any) extends ProjectionNode
  private case class PrimitiveProjectionNode(name: String,
                                             column: String,
                                             table: String,
                                             field: String,
                                             attr: EntityAttribute)
      extends ProjectionNode
  private case class EntityProjectionNode(field: String, fk: Option[(String, String)], branches: Seq[ProjectionNode])
      extends ProjectionNode
  private case class LiftedProjectionNode(fk: Option[(String, String)], branches: Seq[ProjectionNode])
      extends ProjectionNode
  private case class EntityArrayJunctionProjectionNode(entityType: String,
                                                       field: String,
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
  private case class EntityArrayProjectionNode(field: String,
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
  private case class EntityOneProjectionNode(field: String,
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
