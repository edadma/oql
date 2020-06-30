export class Resource {

  getMany(): Promise<any[]>

  insert(obj: any): Promise<any>

}

export class QueryBuilder {

  cond(v: any): QueryBuilder

  add(attribute: QueryBuilder): QueryBuilder

  add(query: String): QueryBuilder
  
  project(resource: string, ...attributes: string[]): QueryBuilder

  query(oql: string): QueryBuilder

  select(oql: string, parameters?: any): QueryBuilder

  order(attribute: string, sorting: string): QueryBuilder

  limit(a: number): QueryBuilder

  offset(a: number): QueryBuilder

  getOne(): Promise<any>

  getMany(): Promise<any[]>

  getCount(): Promise<number>

}

export class OQL {

  constructor(conn: Connection, erd: string)

  entity(resource: string): Resource

  queryBuilder(): QueryBuilder

  queryOne(oql: string, parameters?: any): Promise<any | undefined>

  queryMany(oql: string, parameters?: any): Promise<any[]>

  findOne(resource: string, id: any): Promise<any | undefined>

}

export class PostgresConnection extends Connection {

  constructor(host: string, port: number, database: string, user: string, password: string, ssl: any)

  close(): void

}

export abstract class Connection {

  close(): void

}