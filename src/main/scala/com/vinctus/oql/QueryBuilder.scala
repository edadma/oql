package com.vinctus.oql

import scala.scalajs.js
import js.annotation.JSExport
import js.JSConverters._
import js.{JSON, |}
import scala.collection.immutable.ListMap
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class QueryBuilder private[oql] (private val oql: OQL, private[oql] val q: QueryOQL) {
  private def check = if (q.source eq null) sys.error("QueryBuilder: no resource was given") else this

  private class DoNothingQueryBuilder extends QueryBuilder(oql, q) {
    private def na = sys.error("not applicable")

    @JSExport("cond")
    override def jsCond(v: Any): QueryBuilder = na

    override def cond(b: Boolean): QueryBuilder = na

    @JSExport("getCount")
    override def jsGetCount(): js.Promise[Int] = na

    @JSExport("getMany")
    override def jsGetMany(): js.Promise[js.Array[js.Any]] = na

    @JSExport("getOne")
    override def jsGetOne(): js.Promise[js.Any] = na

    override def getMany: Future[List[ListMap[String, Any]]] = na

    override def getCount: Future[Int] = na

    @JSExport
    override def limit(a: Int): QueryBuilder = QueryBuilder.this

    @JSExport
    override def offset(a: Int): QueryBuilder = QueryBuilder.this

    @JSExport
    override def order(attribute: String, sorting: String): QueryBuilder = QueryBuilder.this

    @JSExport
    override def project(resource: String, attributes: String*): QueryBuilder = QueryBuilder.this

    @JSExport
    override def add(attribute: QueryBuilder): QueryBuilder = QueryBuilder.this

    @JSExport
    override def query(q: String, parameters: js.Any = js.undefined): QueryBuilder = QueryBuilder.this

    @JSExport
    override def select(s: String, parameters: js.Any = js.undefined): QueryBuilder = QueryBuilder.this
  }

  @JSExport("cond")
  def jsCond(v: Any): QueryBuilder = cond(v != () && v != null && v != false && v != 0 && v != "")

  def cond(b: Boolean): QueryBuilder = if (b) this else new DoNothingQueryBuilder

  @JSExport
  def add(attribute: QueryBuilder) =
    new QueryBuilder(
      oql,
      q.copy(project = ProjectAttributesOQL(q.project match {
        case ProjectAttributesOQL(attrs) => attrs :+ attribute.q
        case ProjectAllOQL(_)            => List(attribute.q)
      }))
    )

  @JSExport
  def add(q: String): QueryBuilder = add(query(q))

  @JSExport
  def project(resource: String, attributes: String*): QueryBuilder =
    new QueryBuilder(
      oql,
      if (attributes nonEmpty)
        q.copy(
          source = Ident(resource),
          project = ProjectAttributesOQL(attributes map {
            case "*"                     => ProjectAllOQL()
            case id if id startsWith "-" => NegativeAttribute(Ident(id drop 1))
            case a                       => QueryOQL(Ident(a), ProjectAllOQL(), None, None, None, None, None)
          })
        )
      else
        q.copy(source = Ident(resource))
    )

  @JSExport
  def query(query: String, parameters: js.Any = js.undefined): QueryBuilder =
    new QueryBuilder(oql, OQLParser.parseQuery(template(query, toMap(parameters))))

  @JSExport
  def select(s: String, parameters: js.Any = js.undefined): QueryBuilder = {
    val sel = OQLParser.parseSelect(template(s, toMap(parameters)))

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
  def order(attribute: String, sorting: String): QueryBuilder =
    new QueryBuilder(oql, q.copy(order = Some(List((VariableExpressionOQL(List(Ident(attribute))), sorting)))))

  @JSExport
  def limit(a: Int): QueryBuilder = new QueryBuilder(oql, q.copy(limit = Some(a)))

  @JSExport
  def offset(a: Int): QueryBuilder = new QueryBuilder(oql, q.copy(offset = Some(a)))

  @JSExport("getMany")
  def jsGetMany(): js.Promise[js.Any] = check.oql.jsQueryMany(q)

  @JSExport("getOne")
  def jsGetOne(): js.Promise[js.Any] = check.oql.jsQueryOne(q)

  @JSExport("getCount")
  def jsGetCount(): js.Promise[Int] = getCount.toJSPromise

  def getMany: Future[List[ListMap[String, Any]]] = check.oql.queryMany(q)

  def getOne: Future[Option[ListMap[String, Any]]] = check.oql.queryOne(q)

  def getCount: Future[Int] = getMany map (_.length)

  def json: Future[String] =
    getMany.map(value => JSON.stringify(toJS(value), null.asInstanceOf[js.Array[js.Any]], 2))

}
