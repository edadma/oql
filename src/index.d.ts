export class QueryBuilder {
  constructor(oql: OQL)

  projectResource(resource: string): QueryBuilder

  project(resource: string, ...attributes: string[]): QueryBuilder

  query(oql: string): QueryBuilder

  select(oql: string): QueryBuilder

  orderBy(attribute: string, ascending: boolean): QueryBuilder

  execute(conn: Connection): Promise<any>
}

export class OQL {
  constructor(erd: string)

  query(sql: string, conn: PostgresConnection): Promise<any>
}

export class PostgresConnection extends Connection {
  constructor(user: string, password: string)

  close(): void
}

export abstract class Connection {
  close(): void
}