package xyz.hyperreal.oql

import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}
import scala.collection.immutable.ListMap
import scala.concurrent.Future

@JSExportTopLevel("QueryBuilder")
class QueryBuilder(oql: OQL) {
  private var source: Ident = _
  private var project: ProjectExpressionOQL = ProjectAllOQL
  private var select: Option[ExpressionOQL] = None
  private var group: Option[List[VariableExpressionOQL]] = None
  private var order: Option[List[(ExpressionOQL, Boolean)]] = None
  private var limit: Option[Int] = None
  private var offset: Option[Int] = None

  private def mkQuery =
    if (source ne null)
      QueryOQL(source, project, select, group, order, limit, offset)
    else
      sys.error("QueryBuilder: no resource was given")

  @JSExport
  def projectResource(resource: String): QueryBuilder = {
    source = Ident(resource)
    this
  }

  @JSExport
  def project(resource: String, attributes: String*): QueryBuilder = {
    source = Ident(resource)
    project = ProjectAttributesOQL(
      attributes map (a => QueryOQL(Ident(a), ProjectAllOQL, None, None, None, None, None)))
    this
  }

  @JSExport
  def query(oql: String): QueryBuilder = {
    val q = OQLParser.parseQuery(oql)

    source = q.source
    project = q.project

    if (q.select isDefined)
      select = q.select

    if (q.group isDefined)
      group = q.group

    if (q.order isDefined)
      order = q.order

    if (q.limit isDefined)
      limit = q.limit

    if (q.offset isDefined)
      offset = q.offset

    this
  }

  @JSExport
  def select(oql: String): QueryBuilder = {
    val s = OQLParser.parseSelect(oql)

    select =
      if (select isDefined)
        Some(InfixExpressionOQL(GroupedExpressionOQL(select.get), "AND", GroupedExpressionOQL(s)))
      else
        Some(s)

    this
  }

  @JSExport
  def orderBy(attribute: String, ascending: Boolean): QueryBuilder = {
    order = Some(List((VariableExpressionOQL(List(Ident(attribute))), ascending)))
    this
  }

  @JSExport("execute")
  def jsExecute(conn: Connection): js.Promise[js.Any] = oql.jsQuery(mkQuery, conn)

  def execute(conn: Connection): Future[List[ListMap[String, Any]]] = oql.query(mkQuery, conn)

}
