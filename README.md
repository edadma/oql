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

*OQL* (Object Query Language) is a language for querying a relational database.  The syntax is inspired by GraphQL and is similar, but not identical.  Some capabilities missing from GraphQL have been added, and some capabilities found in GraphQL are implemented differently.  *OQL* only provides support for data retrieval, however there are class methods for performing mutations.  Furthermore, mutation operations all abide by the supplied ER database description, i.e. aliases.  

Some features of *OQL* include:

- very similar to [GraphQL](https://graphql.org/) (for querying)
- uses a very simple [Entity-Relationship model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model) description of the database
- works with the [PostgreSQL database system](https://www.postgresql.org/)
- designed to work with existing databases without having to change the database at all

Installation
------------

This is a [Node.js](https://nodejs.org/en/) module available through the [npm registry](https://www.npmjs.com/).

Installation is done using the [npm install command](https://docs.npmjs.com/downloading-and-installing-packages-locally):

`$ npm install @vinctus/oql`

TypeScript declarations are included in the package.

API
---

The following TypeScript snippet provides an overview of the API.

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection( <host>, <port>, <database>, 
               <user>, <password>, <max>)
const oql = new OQL( <entity-relationship description> )

oql.query(<query>, conn).then((result: any) => <handle result> )
```

`<host>`, `<port>`, `<database>`, `<user>`, `<password>`, and `<max>` are the connection pool (`PoolConfig`) parameters for the Postgres database you are querying.

`<entity-relationship description>` describes the parts of the database being queried.  It's not necessary to describe every field of every table in the database, only what is being retrieved with *OQL*.  However, primary keys of tables that are being queried should always be included, even if you're not interested in retrieving the primary keys themselves.

`<query>` is the OQL query string.

`<handle result>` is your result array handling code.  The `result` object will be predictably structured according to the query.

### Database Description Language

An "Entity-Relationship" style language is used to describe the database.  Only the portions of the database for which OQL is being used need to be described.

#### Syntax

The syntax of the data description language is given using a kind of enhanced [Wirth Syntax Notation](https://en.wikipedia.org/wiki/Wirth_syntax_notation).  The enhancement is the use of a postfix "+" to mean one-or-more repetition of the preceding pattern.  The definition for `json` ([JSON syntax](https://www.json.org/json-en.html)) has been omitted.

```
model = entity+ .
entity = "entity" identifier [ "(" alias ")" ] "{" attribute+ "}" .
digit = "0" | "1" | … | "8" | "9" .
upperCase = "A" | "B" | … | "Y" | "Z" .
lowerCase = "a" | "b" | … | "y" | "z" .
identChar = upperCase | lowerCase | "_" | "$" .
identifier = identChar { identChar | digit } .

alias = identifier .

attribute = [ "*" ] attributeName [ "(" alias ")" ] ":" type [ "!" ]
          | identifier "=" json .

type = primitiveType
     | entityType
     | "[" entityType [ "." attributeName ] "]"
     | "<" entityType [ "." attributeName ] ">"
     | "[" entityType [ "." attributeName ] "]" "(" entityType ")" .

primitiveType = "text"
              | "integer" | "int" | "int4"
              | "bool" | "boolean"
              | "bigint"
              | "decimal"
              | "date"
              | "float" | "float8"
              | "uuid"
              | "timestamp" .

entityType = identifier .

attributeName = identifier .
```

Regarding the `primitiveType` syntax rule, all alternatives on the same line are synonymous.  So, `bool` and `boolean` are synonymous and denote the same type.
 
### Query Language

The query language is inspired by GraphQL. In the following grammar, all keywords (double-quoted string literals) are case-insensitive.

#### Syntax

```
query = identifier [ project ] [ select ] [ group ] [ order ] [ restrict ] .

project = "{" (attributeProject | "-" identifier | "&" identifier | "*")+ "}"
        | "." attributeProject .

attributeProject = identifier "(" [ "*" | identifier ] ")"
                 | "^" query
                 | query .

select = "[" logicalExpression "]" .

variable = ident { "." ident } .

expression = additiveExpression .

logicalExpression = orExpression .

orExpression = andExpression { "OR" andExpression } .

andExpression = notExpression { "AND" notExpression } .

notExpression = "NOT" comparisonExpression
              | comparisonExpression .

comparisonExpression = applyExpression ("<=" | ">=" | "<" | ">" | "=" | "!="
                         | [ "NOT" ] ("LIKE" | "ILIKE")) applyExpression
                     | applyExpression [ "NOT" ] "BETWEEN" applyExpression "AND" applyExpression
                     | applyExpression ("IS" "NULL" | "IS" "NOT" "NULL")
                     | applyExpression [ "NOT" ] "IN" expressions
                     | applyExpression [ "NOT" ] "IN" "(" query ")"
                     | "EXISTS" "(" query ")"
                     | applyExpression .

additiveExpression = multiplicativeExpression { ("+" | "-") multiplicativeExpression } .

multiplicativeExpression = applyExpression { ("*" | "/") applyExpression } .

expressions = "(" expression { "," expression } ")" .

applyExpression = identifier expressions
                | primaryExpression .

primaryExpression = number
                  | string
                  | "TRUE" | "FALSE"
                  | "&" identifier { "." identifier }
                  | "INTERVAL" singleQuoteString
                  | variable
                  | caseExpression
                  | "(" logicalExpression ")" .

caseExpression = "CASE" when+ [ "ELSE" expression ] "END" .

when = "WHEN" logicalExpression "THEN" expression .

expression = applyExpression .

order = "<" orderExpressions ">" .

orderExpressions = orderExpression { "," orderExpression } .

orderExpression = expression [ "ASC" | "DESC" ] [ "NULLS" ("FIRST" | "LAST") ] .

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

Output:

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
  studentid INTEGER REFERENCES students (id),
  classid INTEGER REFERENCES class (id),
  year INTEGER,
  semester TEXT,
  grade TEXT
);

INSERT INTO students (id, stu_name) VALUES
  (1, 'John'),
  (2, 'Debbie');

INSERT INTO class (id, name) VALUES
  (1, 'English'),
  (2, 'Maths'),
  (3, 'Spanish'),
  (4, 'Biology'),
  (5, 'Science'),
  (6, 'Programming'),
  (7, 'Law'),
  (8, 'Commerce'),
  (9, 'Physical Education');

INSERT INTO student_class (studentid, classid, year, semester, grade) VALUES
  (1, 3, 2019, 'fall', 'B+'),
  (1, 5, 2018, 'winter', 'A'),
  (1, 9, 2019, 'summer', 'F'),
  (2, 1, 2018, 'fall', 'A+'),
  (2, 4, 2019, 'winter', 'B-'),
  (2, 5, 2018, 'summer', 'A-'),
  (2, 9, 2019, 'fall', 'B+');
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
      year: integer
      semester: text
      grade: text
    }
  `)

oql
  .query("student { * classes { * students <name> } <name> } [name = 'John']", conn)
  .then((res: any) => console.log(JSON.stringify(res, null, 2)))
```

Output:

```json
[
  {
    "id": 1,
    "name": "John",
    "classes": [
      {
        "id": 9,
        "name": "Physical Education",
        "students": [
          {
            "id": 2,
            "name": "Debbie"
          },
          {
            "id": 1,
            "name": "John"
          }
        ]
      },
      {
        "id": 5,
        "name": "Science",
        "students": [
          {
            "id": 2,
            "name": "Debbie"
          },
          {
            "id": 1,
            "name": "John"
          }
        ]
      },
      {
        "id": 3,
        "name": "Spanish",
        "students": [
          {
            "id": 1,
            "name": "John"
          }
        ]
      }
    ]
  }
]
```

The query `student { * classes { * students <name> } <name> } [name = 'John']` in the above example program is asking for the names of the students enrolled only in the classes in which John is enrolled.  Also, the query is asking for the classes and the students in each class to be ordered by class name and student name, respectively.  The `*` operator is a wildcard that stands for all attributes that do not result in an array value. 

License
-------

[ISC](https://opensource.org/licenses/ISC)