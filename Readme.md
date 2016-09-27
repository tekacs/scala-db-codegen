# scala-db-codegen [![Build Status][travisImg]](travisLink) [![Maven][mavenImg]][mavenLink]
Generate Scala code from your database to use with the incredble library [quill](https://github.com/getquill/quill).
Only tested with postgresql, but could in theory work with any jdbc compliant database.


## What does it do?

Say you have some database with this schema

```sql
create table test_user(
  id integer not null,
  name varchar(255),
  primary key (id)
);

create table article(
  id integer not null,
  author_id integer,
  is_published boolean
);

ALTER TABLE article
  ADD CONSTRAINT author_id_fk
  FOREIGN KEY (author_id)
  REFERENCES test_user (id);
```

`scala-db-codegen` will then generate ["type all the things!"](http://jto.github.io/articles/type-all-the-things/)
code like this

```scala
package com.geirsson.codegen
import java.util.Date
import io.getquill.WrappedValue

object Tables {
  /////////////////////////////////////////////////////
  // Article
  /////////////////////////////////////////////////////
  case class Article(id: Article.Id, authorId: Option[TestUser.Id], isPublished: Option[Article.IsPublished])
  object Article {
    def create(id: Int, authorId: Option[Int], isPublished: Option[Boolean]): Article = {
      Article(Id(id), authorId.map(TestUser.Id.apply), isPublished.map(IsPublished.apply))
    }
    case class Id(value: Int)              extends AnyVal with WrappedValue[Int]
    case class IsPublished(value: Boolean) extends AnyVal with WrappedValue[Boolean]
  }

  /////////////////////////////////////////////////////
  // TestUser
  /////////////////////////////////////////////////////
  case class TestUser(id: TestUser.Id, name: Option[TestUser.Name])
  object TestUser {
    def create(id: Int, name: Option[String]): TestUser = {
      TestUser(Id(id), name.map(Name.apply))
    }
    case class Id(value: Int)      extends AnyVal with WrappedValue[Int]
    case class Name(value: String) extends AnyVal with WrappedValue[String]
  }
}
```

![Type all the things!](https://cdn.meme.am/instances/500x/71298545.jpg)

It could in theory also generate the code differently.

## CLI

Download 13kb bootstrap script
[`scala-db-codegen`](https://github.com/olafurpg/scala-db-codegen/blob/master/scala-db-codegen)
and execute it.
The script will download all dependencies on first execution.

```bash
# print to stdout, works with running postgres instance on
# localhost:5432 with user "postgres", password "postgres" and database "postgres"
$ scala-db-codegen
# Override any default settings with flags.
$ scala-db-codegen --user myuser --password mypassword --url jdbc:postgresql://myhost:8888/postgres --file Tables.scala --type-map "bool,Boolean;int4,Int;int8,Long"
...
```

For more details:
```shell
$ scala-db-codegen --help
Usage: scala-db-codegen [options]
  --usage
        Print usage and exit
  --help | -h
        Print help message and exit
  --user  <value>
        user on database server
  --password  <value>
        password for user on database server
  --url  <value>
        jdbc url
  --schema  <value>
        schema on database
  --jdbc-driver  <value>
        only tested with postgresql
  --imports  <value>
        top level imports of generated file
  --package  <value>
        package name for generated classes
  --type-map  <value>
        Which types should write to which types? Format is: numeric,BigDecimal;int8,Long;...
  --excluded-tables  <value>
        Do not generate classes for these tables.
  --file  <value>
        Write generated code to this filename. Prints to stdout if not set.
```

## Standalone library
[![Maven][mavenImg]][mavenLink]

```scala
// 2.11 only
libraryDependencies += "com.geirsson" %% "scala-db-codegen" % "<version>"
```

Consult the source code, at least for now ;)

## SBT

Clone this repo into a subdirectory of your project. In your build.sbt:

```scala
import sbt.Project.projectToRef
lazy val `scala-db-codegen` = ProjectRef(file("scala-db-codegen"), "scala-db-codegen")
lazy val codegen = project.dependsOn(`scala-db-codegen`)
```

Run from sbt:

```scala
codegen/runMain com.geirsson.codegen.Codegen --package tables --file myfile.scala
```

Hack on the code to further customize to your needs.

## Why not slick-codgen?

The Slick code generator is excellent and please use that if you are using Slick.
Really, the slick codegenerator is extremely customizable and can probably even
do stuff that this library does.

I created this library because I struggled to get the slick code generator
to do exactly what I wanted.
Instead of learning more about slick models and which methods to override
on the slick code generator, I decided to roll my own code generator and
hopefully learn more about jdbc along the way :)

## Changelog

### 0.3.0 (on fork com.tekacs.codegen)

* Major rewrite - two classes remaining from he original code. ;)
* The generator now works in multiple passes, compiler-style - DB fetching, name mapping, type mapping, code generation.
* Reskin code generation on top of scala.meta.
  * Scala keywords are now properly escaped (`type` -> `` `type` ``).
  * scala.meta's guarantees that the generated code's structure is 'correct'.
  * Codegen is now extensible along a few axes and backed by the above guarantees.
* Types are now resolved in a deferred fashion.
  * It's possible to hook in your own completely custom type resolution (override `TypeMapper`).
  * For simpler cases (e.g. Postgres enums), you can simply insert types by overriding `beforeDone` in `TypeMapper`.
    * You can also add a generator in `class Generator` to materialise those types in reaction to them being detected!
  * At the moment knowing when to complete resolution is a hack (since most DB type resolution is single-level, the default `TypeMapper` doesn't provide a real dependency graph).
* A bunch of utilities are included to make working with scala.meta a little easier for this use case.

### 0.2.1

* No longer abort on missing key in `--type-map`, see #3. Thanks @nightscape!

### 0.2.0

* Map nullable columns to `Option` types.
* Rename maven artifact name to `scala-db-codegen` for consistency.

### 0.1.0

* Basic code generation
* Command line interface

[travisImg]: https://travis-ci.org/olafurpg/scala-db-codegen.svg?branch=master
[travisLink]: https://travis-ci.org/olafurpg/scala-db-codegen

[mavenImg]: https://img.shields.io/maven-central/v/com.geirsson/scala-db-codegen_2.11.svg
[mavenLink]: http://search.maven.org/#search%7Cga%7C1%7Ca%3A%22scala-db-codegen_2.11%22%20g%3A%22com.geirsson%22
