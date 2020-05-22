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