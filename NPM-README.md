OQL
===

*Object Query Language* inspired by [GraphQL](https://graphql.org/)

Installation
------------

This is a [Node.js](https://nodejs.org/en/) module available through the [npm registry](https://www.npmjs.com/).

Installation is done using the [npm install command](https://docs.npmjs.com/downloading-and-installing-packages-locally):

`$ npm install @vinctus/oql`

Features
--------

- very similar to [GraphQL](https://graphql.org/)
- takes an easy to write [Entity-Relationship model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model) description of the database
- works with the [PostgreSQL database system](https://www.postgresql.org/)
- designed to work with existing databases

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
    students: [student] enrollment
  }

  entity student {
   *id: integer
    name: text
    classes: [class] enrollment
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