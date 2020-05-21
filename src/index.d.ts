export class OQL {
  constructor(erd: string)

  query(sql: string, conn: PostgresConnection): Promise[Any]
}

export class PostgresConnection {
  constructor(user: String, password: String)

  close():void
}