export class QueryBuilder {

  cond(v: any): QueryBuilder

  add(attribute: QueryBuilder): QueryBuilder

  project(resource: string, ...attributes: string[]): QueryBuilder

  query(oql: string): QueryBuilder

  select(oql: string): QueryBuilder

  order(attribute: string, ascending: boolean): QueryBuilder

  limit(a: number): QueryBuilder

  offset(a: number): QueryBuilder

  getOne(): Promise<any>

  getMany(): Promise<any[]>

  getCount(): Promise<number>
}

export class OQL {
  constructor(erd: string)

  queryBuilder(): QueryBuilder

  query(sql: string, conn: PostgresConnection): Promise<any[]>
}

export class PostgresConnection extends Connection {
  constructor(user: string, password: string)

  close(): void
}

export abstract class Connection {
  close(): void
}