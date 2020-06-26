export class QueryBuilder {

  cond(v: any): QueryBuilder

  add(attribute: QueryBuilder): QueryBuilder

  add(query: String): QueryBuilder
  
  project(resource: string, ...attributes: string[]): QueryBuilder

  query(oql: string): QueryBuilder

  select(oql: string): QueryBuilder

  order(attribute: string, sorting: string): QueryBuilder

  limit(a: number): QueryBuilder

  offset(a: number): QueryBuilder

  getOne(): Promise<any>

  getMany(): Promise<any[]>

  getCount(): Promise<number>
}

export class OQL {
  constructor(erd: string)

  queryBuilder(conn: PostgresConnection): QueryBuilder

  queryOne(sql: string, conn: PostgresConnection): Promise<any | undefined>

  queryMany(sql: string, conn: PostgresConnection): Promise<any[]>

  findOne(resource: string, id: any, conn: Connection): Promise<any | undefined>
}

export class PostgresConnection extends Connection {
  constructor(user: string, password: string)

  close(): void
}

export abstract class Connection {
  close(): void
}