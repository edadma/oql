<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->
**Table of Contents**  *generated with [DocToc](https://github.com/thlorenz/doctoc)*

- [OQL](#oql)
  - [Overview](#overview)
  - [Installation](#installation)
    - [Node.js](#nodejs)
    - [Scala.js](#scalajs)
  - [API](#api)
    - [The `OQL` Class](#the-oql-class)
      - [count(query, [parameters])](#countquery-parameters)
      - [entity(name)](#entityname)
      - [queryBuilder()](#querybuilder)
      - [queryOne(query, [parameters])](#queryonequery-parameters)
      - [queryMany(query, [parameters])](#querymanyquery-parameters)
      - [raw(sql, [values])](#rawsql-values)
    - [The `QueryBuilder` Class](#the-querybuilder-class)
      - [cond(exp)](#condexp)
      - [getCount()](#getcount)
      - [getMany()](#getmany)
      - [getOne()](#getone)
      - [limit(a)](#limita)
      - [offset(a)](#offseta)
      - [order(attribute, sorting)](#orderattribute-sorting)
      - [project(entity, attributes)](#projectentity-attributes)
      - [query(base_query, [parameters])](#querybase_query-parameters)
      - [select(selection, [parameters])](#selectselection-parameters)
    - [The `Resource` Class](#the-resource-class)
      - [delete(id)](#deleteid)
      - [getMany()](#getmany-1)
      - [insert(obj)](#insertobj)
      - [link(e1, attribute, e2)](#linke1-attribute-e2)
      - [unlink(e1, resource, e2)](#unlinke1-resource-e2)
      - [update(e, updates)](#updatee-updates)
  - [Syntax](#syntax)
    - [Data Modeling Language](#data-modeling-language)
      - [Production Rules](#production-rules)
    - [Query Language](#query-language)
      - [Production Rules](#production-rules-1)
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

*OQL* (Object Query Language) is a language for querying a relational database.  The syntax is inspired by GraphQL and is similar, but not identical.  Some capabilities missing from GraphQL have been added, and some capabilities found in GraphQL are implemented differently.  *OQL* only provides support for data retrieval, however there are class methods for performing mutations.  Furthermore, mutation operations all abide by the supplied Entity-Relationship database description, i.e. aliases.  

Some features of *OQL* include:

- very similar to [GraphQL](https://graphql.org/) (for querying)
- uses a very simple [Entity-Relationship model](https://en.wikipedia.org/wiki/Entity%E2%80%93relationship_model) description of the database
- works with the [PostgreSQL database system](https://www.postgresql.org/)
- designed to work with existing databases without having to change the database at all

Installation
------------

### Node.js

There is a [Node.js](https://nodejs.org/en/) module available through the [npm registry](https://www.npmjs.com/).

Installation is done using the [npm install command](https://docs.npmjs.com/downloading-and-installing-packages-locally):

`$ npm install @vinctus/oql`

TypeScript declarations are included in the package.

### Scala.js

There is a [Scala.js](https://www.scala-js.org/) library available through [Github Packages](https://github.com/features/packages).

Add the following lines to your `build.sbt`:

```sbt
externalResolvers += "OQL" at "https://maven.pkg.github.com/vinctustech/oql"

libraryDependencies += "com.vinctus" %%% "-vinctus-oql" % "0.1.47",

npmDependencies in Compile ++= Seq(
  "pg" -> "8.5.1",
  "@types/pg" -> "7.14.7"
)
```

API
---

The TypeScript API is documented first here followed by a few notes on the Scala.js API which is very similar.

The following TypeScript snippet provides an overview of the API.

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection( <host>, <port>, <database>, <user>, <password>, <max>)
const oql = new OQL( conn, <data model> )

oql.query(<query>).then((result: any) => <handle result> )
```

`<host>`, `<port>`, `<database>`, `<user>`, `<password>`, and `<max>` are the connection pool (`PoolConfig`) parameters for the Postgres database you are querying.

`<data model>` describes the parts of the database being queried.  It's not necessary to describe every field of every table in the database, only what is being retrieved with *OQL*.  However, primary keys of tables that are being queried should always be included, even if you're not interested in retrieving the primary keys themselves.

`<query>` is the OQL query string.

`<handle result>` is your result array handling code.  The `result` object will be predictably structured according to the query.

### The `OQL` Class

These are the methods of the `OQL` class, which are the main methods that you will be using. Brackets are a parameter signifies an optional parameter.

#### count(query, [parameters])

Returns a promise for the number of objects where `query` is the query string written in the [OQL query language](#query-language). If `parameters` is given, each parameter is referenced in the query as `:name` where `name` is the name of the parameter.

For example

```typescript
oql.count('product [price < :max]', {max: 100.00})
```

gets the number of products that are less than $100.

#### entity(name)

Returns a `Resource` instance for OQL class instance that it was called on. See [The `Resource` Class](#the-resource-class) for a method reference.

#### queryBuilder()

Returns a `QueryBuilder` instance for OQL class instance that it was called on. See [The `QueryBuilder` Class](#the-querybuilder-class) for a method reference.

#### queryOne(query, [parameters])

Returns a promise for zero or one object where `query` is the query string written in the [OQL query language](#query-language). If `parameters` is given, each parameter is referenced in the query as `:name` where `name` is the name of the parameter.

For example

```typescript
oql.queryOne('user {id name email} [id < :id]', {id: 12345})
```

gets the `id`, `name`, and `email` for user with id 12345.

#### queryMany(query, [parameters])

Returns a promise for an array of objects where `query` is the query string written in the [OQL query language](#query-language). If `parameters` is given, each parameter is referenced in the query as `:name` where `name` is the name of the parameter.

For example

```typescript
oql.queryMany('product {id name price supplier.name} [price < :max]', {max: 100.00})
```

gets the `id`, `name`, `price` and `supplier.name` for products that are less than $100.

#### raw(sql, [values])

Perform the raw SQL query and return a promise for the results where `sql` is the query string and `values` are query parameter values. 

### The `QueryBuilder` Class

`QueryBuilder` is used to build up a query step by step. `QueryBuilder` instances are immutable so each method that returns a `QueryBuilder` object is returning a new instance.

#### cond(exp)

Blocks the next method call in a chain of `QueryBuilder` calls if the condition expression `exp` is falsy.

#### getCount()

Returns a promise for the number of objects that could be retrieved with `this` query builder.

#### getMany()

Returns a promise for an array of object specified by `this` query builder.

#### getOne()

Returns a promise for zero or one object specified by `this` query builder.

#### limit(a)

Returns a new query builder with a query limit of `a`.

#### offset(a)

Returns a new query builder with a query offset of `a`.

#### order(attribute, sorting)

Returns a new query builder with a query ordering on `attribute` with `sorting` direction.

#### project(entity, attributes)

Returns a new query builder to query `entiry` retrieving `attributes`. This method is a bit more efficient than using `query` because it avoids parsing the query. 

#### query(base_query, [parameters])

Returns a new query builder with the given `base_query`, which must be a well-formed OQL query.

#### select(selection, [parameters])

Returns a new query builder with the given `selection`.  If a selection has been given, either using `query` (with the selection within brackets) or using `select`, then this selection will be logically AND'ed with the previous one. There or no need to add parentheses to ensure correct order of operations if the selection contains a logical OR, this is done internally.

### The `Resource` Class

These are methods that can be called on a resource object.

#### delete(id)

Returns a promise to delete object with primary key `id`.

#### getMany()

Returns a promise for all objects of `this` resource.

#### insert(obj)

Returns a promise to insert `obj` into `this` resource. The promise resolves to an object with the primary key of the inserted object.

#### link(e1, attribute, e2)

Returns a promise to link object `e1` of `this` resource to object `e2` of the type given for `attribute`.

#### unlink(e1, resource, e2)

Returns a promise to unlink object `e1` of `this` resource to object `e2` of the type given for `attribute`.

#### update(e, updates)

Returns a promise to unlink object `e` of `this` resource according to `updates`.

Syntax
------

The syntax of both the data modeling language and the query language is given using a kind of enhanced [Wirth Syntax Notation](https://en.wikipedia.org/wiki/Wirth_syntax_notation).  The enhancement is just the use of a postfix "+" to mean one-or-more repetition of the preceding pattern.  The definition for `json` ([JSON syntax](https://www.json.org/json-en.html)) has been omitted.

### Data Modeling Language

An "Entity-Relationship" style language is used to describe the database.  Only the portions of the database for which OQL is being used need to be described.

#### Production Rules

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

#### Production Rules

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
                  | "(" query ")"
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

CREATE TABLE department (
  dep_id SERIAL PRIMARY KEY,
  dep_name TEXT
);

CREATE TABLE employee (
  emp_id SERIAL PRIMARY KEY,
  emp_name TEXT,
  job_title TEXT,
  manager_id INTEGER REFERENCES employee,
  dep_id INTEGER REFERENCES department
);

INSERT INTO department (dep_id, dep_name) VALUES
  (1001, 'FINANCE'),             
  (2001, 'AUDIT'),
  (3001, 'MARKETING');

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
```

Run the following TypeScript program:

```typescript
import { OQL, PostgresConnection } from '@vinctus/oql'

const conn = new PostgresConnection("localhost", 5432, "postgres", 'postgres', 'docker', false)
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

This example presents a very simple "student" database where students are enrolled in classes, so that the students and classes are in a *many-to-many* relationship.  The example has tables and fields that are intentionally poorly named so as to demonstrate the aliasing features of the database modeling language.

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

const conn = new PostgresConnection("localhost", 5432, "postgres", 'postgres', 'docker', false)
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