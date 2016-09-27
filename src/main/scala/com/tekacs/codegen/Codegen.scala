package com.tekacs.codegen

import java.io.{File, PrintStream}
import java.nio.file.{Files, Paths}
import java.sql.{Connection, DriverManager, ResultSet}

import caseapp.{AppOf, _}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.meta._

case class Error(msg: String) extends Exception(msg)

@AppName("db-codegen")
@AppVersion(Versions.nightly)
@ProgName("db-codegen")
case class CodegenOptions(
    @HelpMessage("user on database server") user: String = "postgres",
    @HelpMessage("password for user on database server") password: String =
      "postgres",
    @HelpMessage("jdbc url") url: String = "jdbc:postgresql:postgres",
    @HelpMessage("schema on database") schema: String = "public",
    @HelpMessage("only tested with postgresql") jdbcDriver: String =
      "org.postgresql.Driver",
    @HelpMessage(
      "top level imports of generated file"
    ) imports: List[String] = List("io.getquill.WrappedValue"),
    @HelpMessage(
      "package name for generated classes"
    ) `package`: String = "tables",
    @HelpMessage(
      "Which types should write to which types? Format is: numeric,BigDecimal;int8,Long;..."
    ) typeMap: TypeMap = TypeMap.default,
    @HelpMessage(
      "Do not generate classes for these tables."
    ) excludedTables: List[String] = List("schema_version"),
    @HelpMessage(
      "Write generated code to this filename. Prints to stdout if not set."
    ) file: Option[String] = None
) extends App {
  Codegen.cliRun(this)
}

case class Codegen() {
}

object Codegen extends AppOf[CodegenOptions] {
  def debugPrintColumnLabels(rs: ResultSet): Unit = {
    (1 to rs.getMetaData.getColumnCount).foreach { i =>
      println(i -> rs.getMetaData.getColumnLabel(i))
    }
  }

  def cliRun(codegenOptions: CodegenOptions,
             outstream: PrintStream = System.out): Unit = {
    try {
      run(codegenOptions, outstream)
    } catch {
      case Error(msg) =>
        System.err.println(msg)
        System.exit(1)
    }
  }

  def run(options: CodegenOptions,
          outstream: PrintStream = System.out): Unit = {
    options.file.foreach { x =>
      outstream.println("Starting...")
    }

    val startTime = System.currentTimeMillis()
    Class.forName(options.jdbcDriver)
    val db: Connection =
      DriverManager.getConnection(options.url,
                                  options.user,
                                  options.password)

    val tables = new DatabaseOps(db, options.schema, options.excludedTables.toSet).tables
    val mapper = new NameMapper(SnakeCaseReverse)
    val typeMapper = new TypeMapper(mapper)
    val meta = new Meta


    val mainFuture = for {
      generatedCode <- new Generator(tables, options, mapper, typeMapper, meta).makePackage
      generatedText = generatedCode.syntax
    } yield {
      options.file match {
        case Some(uri) =>
          Files.write(Paths.get(new File(uri).toURI), generatedText.getBytes)
          println(
            s"Done! Wrote to $uri (${System.currentTimeMillis() - startTime}ms)")
        case _ =>
          outstream.println(generatedText)
      }

      db.close()
    }

    Await.ready(mainFuture, 100.minutes)
  }
}
