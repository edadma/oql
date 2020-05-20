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
