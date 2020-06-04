package xyz.hyperreal.oql

class QueryBuilder {
  var source: Ident = _
  var project: ProjectExpressionOQL = _
  var select: Option[ExpressionOQL]
  var group: Option[List[VariableExpressionOQL]]
  var order: Option[List[(ExpressionOQL, Boolean)]]
  var limit: Option[Int]
  var offset: Option[Int]
}
