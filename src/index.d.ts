export class OQL {
  constructor(erd: string)

  query(sql: string, conn: PostgresConnection): Promise<any>
}

export class PostgresConnection {
  constructor(user: string, password: string)

  close():void
}