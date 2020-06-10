package com.vinctus.oql

import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}
import js.JSConverters._
import js.JSON
import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class QueryBuilder private[oql] (private val oql: OQL, q: QueryOQL) {
  private def check = if (q.source eq null) sys.error("QueryBuilder: no resource was given") else this

  @JSExport
  def projectResource(resource: String): QueryBuilder =
    new QueryBuilder(oql, QueryOQL(Ident(resource), q.project, q.select, q.group, q.order, q.limit, q.offset))

  @JSExport
  def project(resource: String, attributes: String*): QueryBuilder =
    new QueryBuilder(
      oql,
      q.copy(source = Ident(resource),
             project = ProjectAttributesOQL(
               attributes map (a => QueryOQL(Ident(a), ProjectAllOQL, None, None, None, None, None))))
    )

  @JSExport
  def query(q: String): QueryBuilder = new QueryBuilder(oql, OQLParser.parseQuery(q))

  @JSExport
  def select(s: String): QueryBuilder = {
    val sel = OQLParser.parseSelect(s)

    new QueryBuilder(
      oql,
      q.copy(
        select =
          if (q.select isDefined)
            Some(InfixExpressionOQL(GroupedExpressionOQL(q.select.get), "AND", GroupedExpressionOQL(sel)))
          else
            Some(sel))
    )
  }

  @JSExport
  def order(attribute: String, ascending: Boolean): QueryBuilder =
    new QueryBuilder(oql, q.copy(order = Some(List((VariableExpressionOQL(List(Ident(attribute))), ascending)))))

  @JSExport
  def limit(a: Int): QueryBuilder = new QueryBuilder(oql, q.copy(limit = Some(a)))

  @JSExport
  def offset(a: Int): QueryBuilder = new QueryBuilder(oql, q.copy(offset = Some(a)))

  @JSExport("execute")
  def jsExecute(conn: Connection): js.Promise[js.Any] = check.oql.jsQuery(q, conn)

  @JSExport("count")
  def jsCount(conn: Connection): js.Promise[Int] = count(conn).toJSPromise

  def execute(conn: Connection): Future[List[ListMap[String, Any]]] = check.oql.query(q, conn)

  def count(conn: Connection): Future[Int] = execute(conn) map (_.length)

  def json(conn: Connection): Future[String] =
    execute(conn).map(value => JSON.stringify(toJS(value), null.asInstanceOf[js.Array[js.Any]], 2))

}
