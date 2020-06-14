<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [OQL](#oql)
  - [Overview](#overview)
  - [Installation](#installation)
  - [API](#api)
    - [Database Description Language](#database-description-language)
      - [Syntax](#syntax)
    - [Query Language](#query-language)
      - [Syntax](#syntax-1)
  - [Examples](#examples)
    - [Example (many-to-one)](#example-many-to-one)
    - [Example (many-to-many)](#example-many-to-many)
  - [License](#license)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

OQL
===

*Object Query Language* inspired by [GraphQL](https://graphql.org/)

Overview
--------

*OQL* (Object Query Language) is a language for querying a relational database.  The query syntax is inspired by GraphQL, but is not identical.  Some capabilities that are not in GraphQL have been added, and some capabilities of GraphQL are done differently.  *OQL* only provides support for data retrieval and not mutations of any kind.

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

entity = "entity" identifier [ "(" alias ")" ] "{" attribute+ "}" .

alias = identifier .

attribute = identifier [ "(" alias ")" ] ":" type
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

The query language is inspired by GraphQL.  Exactly the data that is needed is requested, so as to avoid circularity.

#### Syntax

```
query = identifier [ project ] [ select ] [ group ] [ order ] [ restrict ]

project = "{" attributeProject+ "}"
        | "." attributeProject .

attributeProject = identifier "(" [ "*" | identifier ] ")"
                 | "*"
                 | query .

select = "[" logicalExpression "]" .

logicalExpression = orExpression .

orExpression = andExpression { ("OR" | "or") andExpression } .

andExpression = notExpression { ("AND" | "and") notExpression } .

notExpression = ("NOT" | "not") comparisonExpression
              | comparisonExpression .

comparisonExpression = applyExpression ("<=" | ">=" | "<" | ">" | "=" | "!=" | (["NOT" | "not"]
                         ("LIKE" | "like" | "ILIKE" | "ilike")) applyExpression
                     | applyExpression [ "NOT" | "not" ] ("BETWEEN" | "between") applyExpression
                         ("AND" | "and") applyExpression
                     | applyExpression ((("IS" | "is") ("NULL" | "null")) |
                         (("IS" | "is") ("NOT" | "not") ("NULL" | "null")))
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

caseExpression = ("CASE" | "case") when+ [ ("ELSE" | "else") expression ]
                   ("END" | "end") .

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

Examples
--------

Several examples are presented, each one creating a different database.  Each example presents a different use case.

### Example (many-to-one)

This example presents a very simple "employee" database where employees have a manager and a department (among other things), so that the employees and their managers are in a *many-to-one* relationship, which also requires that the employee table to joined to itself.  Employees and departments are also in a *many-to-one* relationship.

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
CREATE DATABASE employees;

CREATE TABLE employee (
  emp_id SERIAL PRIMARY KEY,
  emp_name TEXT,
  job_title TEXT,
  manager_id INTEGER REFERENCES employee,
  dep_id INTEGER REFERENCES department
);

CREATE TABLE department (
  dep_id SERIAL PRIMARY KEY,
  dep_name TEXT
);

INSERT INTO employee (emp_id, emp_name, job_title, manager_id, dep_id) VALUES
  (68319, 'KAYLING', 'PRESIDENT', null, 1001),
  (66928, 'BLAZE', 'MANAGER', 68319, 3001),
  (67832, 'CLARE', 'MANAGER', 68319, 1001),
  (65646, 'JONAS', 'MANAGER', 68319, 2001),
  (67858, 'SCARLET', 'ANALYST', 65646, 2001),
  (69062, 'FRANK', 'ANALYST', 65646, 2001),
  (63679, 'SANDRINE', 'CLERK', 69062, 2001),
  (64989, 'ADELYN', 'SALESREP', 66928, 3001),
  (65271, 'WADE', 'SALESREP', 66928, 3001),
  (66564, 'MADDEN', 'SALESREP', 66928, 3001),
  (68454, 'TUCKER', 'SALESREP', 66928, 3001),
  (68736, 'ADNRES', 'CLERK', 67858, 2001),
  (69000, 'JULIUS', 'CLERK', 66928, 3001),
  (69324, 'MARKER', 'CLERK', 67832, 1001);

INSERT INTO department (dep_id, dep_name) VALUES
  (1001, 'FINANCE'),             
  (2001, 'AUDIT'),
  (3001, 'MARKETING');
```

Run the following TypeScript program:

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection('postgres', 'docker')
const oql = 
  new OQL(`
    entity employee {
     *emp_id: integer
      name (emp_name): text
      job_title: text
      manager (manager_id): employee
      department (dep_id): department
    }
    
    entity department {
     *dep_id: integer
      name (dep_name): text
    }
  `)

oql
  .query("employee { name manager.name department.name } [job_title = 'CLERK']", conn)
  .then((res: any) => console.log(JSON.stringify(res, null, 2)))
```

You should see the following output:

```json
[
  {
    "name": "SANDRINE",
    "manager": {
      "name": "FRANK"
    },
    "department": {
      "name": "AUDIT"
    }
  },
  {
    "name": "ADNRES",
    "manager": {
      "name": "SCARLET"
    },
    "department": {
      "name": "AUDIT"
    }
  },
  {
    "name": "JULIUS",
    "manager": {
      "name": "BLAZE"
    },
    "department": {
      "name": "MARKETING"
    }
  },
  {
    "name": "MARKER",
    "manager": {
      "name": "CLARE"
    },
    "department": {
      "name": "FINANCE"
    }
  }
]
```

The query `employee { name manager.name department.name } [job_title = 'CLERK']` in the above example program is asking for the names of employees with job title "CLERK" as well as the names of their manager and department.  In standard GraphQL, the query would have been `{ employee(job_title: 'CLERK') { name manager { name } department { name } } }`, which we feel is more verbose than it needs to be.  The use of dot notation as in `manager.name` is semantically equivalent to `manager { name }`. 

### Example (many-to-many)

This example presents a very simple "student" database where students are enrolled in classes, so that the students and classes are in a *many-to-many* relationship.  The example has tables and fields that are intentionally poorly named so as to demonstrate the aliasing features of the database description language.

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

Run the following TypeScript program:

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

The query `class { name students.name }` in the above example program is asking for the names of the students enrolled in each class.  In standard GraphQL, the query would have been `{ class { name students { name } } }`, which we feel is more verbose than it needs to be.  The use of dot notation as in `students.name` is semantically equivalent to `students { name }`. 

License
-------

[ISC](https://opensource.org/licenses/ISC)