OQL
===

*Object Query Language* inspired by [GraphQL](https://graphql.org/)

Overview
--------

*OQL* (Object Query Language) is a language for querying a relational database. The query syntax is inspired by GraphQL, but is not identical.  *OQL* only provides support for data retrieval and not mutations of any kind.

Some features of *OQL* include:

- very similar to [GraphQL](https://graphql.org/) (for querying)
- uses an easy to write [Entity-Relationship model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model) description of the database
- works with the [PostgreSQL database system](https://www.postgresql.org/)
- designed to work with existing databases

Installation
------------

This is a [Node.js](https://nodejs.org/en/) module available through the [npm registry](https://www.npmjs.com/).

Installation is done using the [npm install command](https://docs.npmjs.com/downloading-and-installing-packages-locally):

`$ npm install @vinctus/oql`

API
---

The following TypeScript snippet provides an overview of the API.

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection( <database username>, <database password>)
const oql = new OQL( <entity-relationship description> )

oql.query('class { name students.name }', conn).then((result: any) => <handle result> )
```

`<database username>` and `<database password>` are the username and password of the Postgres database you are querying.

`<entity-relationship description>` describes the parts of the database being queried.  It's not necessary to describe every field of every table in the database, only what is being retrieved with *OQL*.  Primary keys of relevant tables should always be included, even if you're not interested in retrieving them.

`<handle result>` is your result array handling code.  The `result` object will predictably by structured according to the query.

Example
-------

Get [PostgreSQL](https://hub.docker.com/_/postgres) running in a [docker container](https://www.docker.com/resources/what-container):

```
docker pull postgres
docker run --rm --name pg-docker -e POSTGRES_PASSWORD=docker -d -p 5432:5432 postgres
```

Run the [PostgreSQL terminal](https://www.postgresql.org/docs/9.3/app-psql.html) to create a database:

`psql -h localhost -U postgres -d postgres`

Create a simple database by copy-pasting the following (yes, all in one shot) at the `psql` prompt:

```sql
CREATE DATABASE student;

CREATE TABLE student (
  id SERIAL PRIMARY KEY,
  name TEXT
);

CREATE TABLE class (
  id SERIAL PRIMARY KEY,
  name TEXT
);

CREATE TABLE enrollment (
  studentid INTEGER REFERENCES student,
  classid INTEGER REFERENCES class
);

INSERT INTO student (name) VALUES
  ('John'),
  ('Debbie');

INSERT INTO class (name) VALUES
  ('English'),
  ('Maths'),
  ('Spanish');

INSERT INTO enrollment (studentid, classid) VALUES
  (1, 3),
  (1, 1),
  (2, 1),
  (2, 2);
```

Create the following TypeScript source:

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection('postgres', 'docker')
const oql = new OQL(`
  entity class {
   *id: integer
    name: text
    students: [student] (enrollment)
  }

  entity student {
   *id: integer
    name: text
    classes: [class] (enrollment)
  }
  
  entity enrollment {
    student (studentid): student
    class (classid): class
  }`)

oql.query('class { name students.name }', conn).then((res: any) => console.log(JSON.stringify(res, null, 2)))
```

You should see the following output:

```json
[
  {
    "name": "English",
    "students": [
      {
        "name": "John"
      },
      {
        "name": "Debbie"
      }
    ]
  },
  {
    "name": "Maths",
    "students": [
      {
        "name": "Debbie"
      }
    ]
  },
  {
    "name": "Spanish",
    "students": [
      {
        "name": "John"
      }
    ]
  }
]
```

License
-------

[ISC](https://opensource.org/licenses/ISC)