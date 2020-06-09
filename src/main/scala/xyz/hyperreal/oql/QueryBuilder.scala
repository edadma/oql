package xyz.hyperreal.oql

import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}
import js.JSConverters._

import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

@JSExportTopLevel("QueryBuilder")
class QueryBuilder private (oql: OQL, q: QueryOQL) {
  def this(oql: OQL) = this(oql, QueryOQL(null, ProjectAllOQL, None, None, None, None, None))

  private def check = sys.error("QueryBuilder: no resource was given")

  @JSExport
  def projectResource(resource: String): QueryBuilder =
    new QueryBuilder(oql, QueryOQL(Ident(resource), q.project, q.select, q.group, q.order, q.limit, q.offset))

  @JSExport
  def project(resource: String, attributes: String*): QueryBuilder =
    new QueryBuilder(
      oql,
      QueryOQL(
        Ident(resource),
        ProjectAttributesOQL(attributes map (a => QueryOQL(Ident(a), ProjectAllOQL, None, None, None, None, None))),
        q.select,
        q.group,
        q.order,
        q.limit,
        q.offset
      )
    )

  @JSExport
  def query(oql: String): QueryBuilder = {
    val q = OQLParser.parseQuery(oql)

    source = q.source
    project = q.project
    group = None
    order = None
    select = None
    limit = None

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

  @JSExport
  def limit(a: Int): QueryBuilder = {
    limit = Some(a)
    this
  }

  @JSExport
  def offset(a: Int): QueryBuilder = {
    offset = Some(a)
    this
  }

  @JSExport("execute")
  def jsExecute(conn: Connection): js.Promise[js.Any] = oql.jsQuery(mkQuery, conn)

  @JSExport("count")
  def jsCount(conn: Connection): js.Promise[Int] = count(conn).toJSPromise

  def execute(conn: Connection): Future[List[ListMap[String, Any]]] = oql.query(mkQuery, conn)

  def count(conn: Connection): Future[Int] = execute(conn) map (_.length)

}
