<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [OQL](#oql)
  - [Overview](#overview)
  - [Installation](#installation)
  - [API](#api)
  - [Example](#example)
  - [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

OQL
===

*Object Query Language* inspired by [GraphQL](https://graphql.org/)

Overview
--------

*OQL* (Object Query Language) is a language for querying a relational database.  The query syntax is inspired by GraphQL, but is not identical.  Some capabilities that GraphQL doesn't have been added, and some capabilities of GraphQL are done differently.  *OQL* only provides support for data retrieval and not mutations of any kind.

Some features of *OQL* include:

- very similar to [GraphQL](https://graphql.org/) (for querying)
- uses an easy to write [Entity-Relationship model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model) description of the database
- works with the [PostgreSQL database system](https://www.postgresql.org/)
- designed to work with existing databases without having to change the database at all

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

oql.query(<query>, conn).then((result: any) => <handle result> )
```

`<database username>` and `<database password>` are the username and password of the Postgres database you are querying.

`<entity-relationship description>` describes the parts of the database being queried.  It's not necessary to describe every field of every table in the database, only what is being retrieved with *OQL*.  Primary keys of relevant tables should always be included, even if you're not interested in retrieving them.

`<query>` is the OQL query string.

`<handle result>` is your result array handling code.  The `result` object will predictably by structured according to the query.

### Database Description Language

An "Entity-Relationship" style language is used to describe the database.  Only the portions of the database the OQL is being applied to need to be described.

#### Syntax

The syntax of the data description language is given using a kind of enhanced [Wirth Syntax Notation](https://en.wikipedia.org/wiki/Wirth_syntax_notation).  Definitions for `json` ([json syntax](http://www.ecma-international.org/publications/files/ECMA-ST/ECMA-404.pdf)) and `identifier` have been omitted.

```
model = entity+ .

entity = "entity" "{" attribute+ "}" .

attribute = identifier ":" type
          | identifier "=" json .

type = primitiveType
     | entityType
     | "[" entityType "]"
     | "[" entityType "]" "(" entityType ")" .

primitiveType = "text"
              | "integer"
              | "double"
              | "bigint"
              | "timestamp" .

entityType = identifier .
```

### Query Language

#### Syntax

```
query = identifier [ project ] [ select ] [ group ] [ order ] [ restrict ]

project = "{" attributeProject+ "}"
        | "." attributeProject .

attributeProject = identifier "(" identifier ")"
                 | query .

select = "[" logicalExpression "]" .

logicalExpression = orExpression .

orExpression = andExpression { ("OR" | "or") andExpression } .

andExpression = notExpression { ("AND" | "and") notExpression } .

notExpression = ("NOT" | "not") comparisonExpression
              | comparisonExpression .

comparisonExpression = applyExpression ("<=" | ">=" | "<" | ">" | "=" | "!=" | ("LIKE" | "like" | "ILIKE" | "ilike") |
                         (("NOT" | "not") ("LIKE" | "like" | "ILIKE" | "ilike")) applyExpression
                     | applyExpression ((("IS" | "is") ("NULL" | "null")) | (("IS" | "is") ("NOT" | "not")
                         ("NULL" | "null")))
                     | applyExpression (("IN" | "in") | (("NOT" | "not") ("IN" | "in"))) expressions
                     | applyExpression .

expressions = "(" expression { "," expression } ")" .

applyExpression = identifier expressions
                | primaryExpression .

primaryExpression = number
                  | string
                  | identifier { "." identifier } "#"
                  | variable
                  | caseExpression
                  | "(" logicalExpression ")" .

caseExpression = ("CASE" | "case") when+ [ ("ELSE" | "else") expression ] ("END" | "end") .

when = ("WHEN" | "when") logicalExpression ("THEN" | "then") expression .

expression = applyExpression .

order = "<" orderExpressions ">" .

orderExpressions = orderExpression { "," orderExpression } .

orderExpression = expression [ "/" | "\\" ] .

group = "(" variables ")" .

variables = variable { "," variable } .

restrict = "|" integer [ "," integer ] "|"
         | "|" "," integer "|" .
```

Example
-------

Get [PostgreSQL](https://hub.docker.com/_/postgres) running in a [docker container](https://www.docker.com/resources/what-container):

```
sudo docker pull postgres
sudo docker run --rm --name pg-docker -e POSTGRES_PASSWORD=docker -d -p 5432:5432 postgres
```

Run the [PostgreSQL terminal](https://www.postgresql.org/docs/9.3/app-psql.html) to create a database:

`psql -h localhost -U postgres -d postgres`

which can be installed if necessary with the command 

`sudo apt-get install postgresql-client`

Create a simple database by copy-pasting the following (yes, all in one shot) at the `psql` prompt:

```sql
CREATE DATABASE student;

CREATE TABLE students (
  id SERIAL PRIMARY KEY,
  stu_name TEXT
);

CREATE TABLE class (
  id SERIAL PRIMARY KEY,
  name TEXT
);

CREATE TABLE student_class (
  studentid INTEGER REFERENCES students,
  classid INTEGER REFERENCES class
);

INSERT INTO students (stu_name) VALUES
  ('John'),
  ('Debbie');

INSERT INTO class (name) VALUES
  ('English'),
  ('Maths'),
  ('Spanish');

INSERT INTO student_class (studentid, classid) VALUES
  (1, 3),
  (1, 1),
  (2, 1),
  (2, 2);
```

Create the following TypeScript source:

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection('postgres', 'docker')
const oql = 
  new OQL(`
    entity class {
     *id: integer
      name: text
      students: [student] (enrollment)
    }

    entity student (students) {
     *id: integer
      name (stu_name): text
      classes: [class] (enrollment)
    }
  
    entity enrollment (student_class) {
      student (studentid): student
      class (classid): class
    }
  `)

oql
  .query('class { name students.name }', conn)
  .then((res: any) => console.log(JSON.stringify(res, null, 2)))
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