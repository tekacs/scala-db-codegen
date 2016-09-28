package com.tekacs.codegen

import com.tekacs.codegen.DatabaseOps._
import com.tekacs.codegen.Generator.HiddenImplicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.meta._

class Generator[N <: NameMapper](val tables: Seq[DatabaseOps.Table],
                                 val codegenOptions: CodegenOptions,
                                 val mapper: N,
                                 val typeMapper: TypeMapper[N],
                                 val meta: Meta)(implicit ec: ExecutionContext) {
  import Generator._
  import Meta._
  import NameMapper._
  import mapper._

  for {
    (dbTypeString, exactType) <- codegenOptions.typeMap.pairs
    typeName = TypeName(dbTypeString)
  } typeMapper.found(typeName)(new NamedType(exactType)(ExactNamer))

  def makePackage: Future[Pkg] = for { topLevel <- makeTopLevel }
    yield meta.packageFor(
      codegenOptions.`package`.parse[Term].get.asInstanceOf[Term.Ref],
      codegenOptions.imports.map(_.parse[Importer].get),
      topLevel
    )

  def makeTopLevel: Future[Seq[Stat]] = for { tableCode <- translate(tables) }
    yield Seq(
      meta.objectFor(
        Term.Name("Tables"),
        tableCode.flatMap(_.statements)
      )
    )

  def translateLogicalType(column: Column): Future[Option[NamedType[_]]] = {
    val scalaType = column.ref match {
      case Some(mappedColumn) =>
        typeMapper.found(column.name)(mappedColumn)
        mappedColumn
      case None =>
        typeMapper.autoFound(column.name)
        column.name
    }
    typeMapper.request(scalaType)
  }

  def translateDbType(column: Column): Future[Option[NamedType[_]]] = {
    typeMapper.request(column.dbType)
  }

  def translate(tables: Seq[Table]): Future[Seq[GeneratedForm]] = {
    // FIXME: This is a hack.
    tables.flatMap(_.columns).map(c => { translateLogicalType(c); translateDbType(c) })
    typeMapper.done()

    Future.sequence(tables.map(translate)).map(_.flatten)
  }

  def translate(table: Table): Future[GeneratedForm] = {
    implicit val columnOrdering = Ordering.by { col: Column => col.name.column }
    for {
      // Get code, logical type and DB type for all columns.
      columnCode <- Future.sequence(table.columns.map(translate))
      columnTypes <- Future.sequence(table.columns.map(translateLogicalType))
      columnDbTypes <- Future.sequence(table.columns.map(translateDbType))
    } yield {
      // Turn the above into something usable. Drop columns where data was unavailable (warn later).
      val allColumnData = (table.columns zip columnTypes zip columnDbTypes)
        .map { case ((a, b), c) => (a, b, c) }

      val filteredColumnData = allColumnData
        .filterNot { case (_, typ, dbType) => typ.isEmpty || dbType.isEmpty }

      val skippedColumnData = allColumnData
        .filter { case (_, typ, dbType) => typ.isEmpty || dbType.isEmpty }

      val columnData = filteredColumnData
        .map { case (c, o1, o2) => (c, o1.get, o2.get) }

      skippedColumnData.map { case (col, logicalType, storageType) => (
          s"Could not generate column ${col.name} due to missing "
            + (if (logicalType.isEmpty) s"logical type for ${col.ref.getOrElse(col.name)}" else "")
            + (if (storageType.isEmpty) s"storage type for ${col.dbType}" else "")
        )}.foreach(System.err.println)

      // Create parameter list for the table class.
      val params = columnData.map { case (col, typ, _) =>
        if (col.isNullable)
          param"${col.name.fieldName}: Option[${typ.qualifiedClassName}]"
        else
          param"${col.name.fieldName}: ${typ.qualifiedClassName}"
      }

      // Create `def create(id: Int, ...) = Table(TableName.Id(id), ...)` convenience function.
      val creatorParams = columnData.map { case (col, _, dbType) =>
        if (col.isNullable)
          param"${col.name.fieldName}: Option[${dbType.qualifiedClassName}]"
        else
          param"${col.name.fieldName}: ${dbType.qualifiedClassName}" }
      val creatorArgs = columnData.map { case (col, typ, _) =>
        if (col.isNullable)
          arg"${col.name.fieldName}.map(${typ.qualifiedClassName.constructor}.apply)"
        else
          arg"${typ.qualifiedClassName.constructor}(${col.name.fieldName})"
      }
      val creator =
        q"""def create(..$creatorParams): ${table.name.qualifiedClassName} = {
           ${table.name.qualifiedClassName.constructor}(..$creatorArgs)
        }""".asInstanceOf[Stat]

      // Grab full sequence of generated column code.
      val companionBody = Seq(creator) ++ columnCode.filter(_.isDefined).flatMap(_.get.statements)

      FullForm(
        mainClass = meta.classFor(
          table.name.className,
          params,
          Seq.empty,
          Seq.empty
        ),
        companion = meta.objectFor(
          Term.Name(table.name.className.value),
          companionBody,
          Seq.empty
        )
      )
    }
  }

  def translate(column: Column): Future[Option[GeneratedForm]] = {
    for {
      logicalTypeOpt <- translateLogicalType(column)
      storageTypeOpt <- translateDbType(column)
    } yield for {
      logicalType <- logicalTypeOpt
      storageType <- storageTypeOpt

      // Don't generate column /classes/ where backed by another column.
      if logicalType.name == column.name
    } yield
      ClassForm(
        main = meta.classFor(
          column.name.className,
          Seq(param"value: ${storageType.qualifiedClassName}"),
          Seq.empty,
          Seq(t"AnyVal", t"WrappedValue[${storageType.qualifiedClassName}]")
        )
      )
  }
}

object Generator {
  object HiddenImplicits {
    implicit def singleElementToSeq[T](el: T): Seq[T] = Seq(el)
  }

  trait GeneratedForm {
    def statements: Seq[Stat]
  }

  case class FullForm(mainClass: Defn.Class, companion: Defn.Object,
                      before: Seq[Stat] = Seq.empty, after: Seq[Stat] = Seq.empty)
    extends GeneratedForm {
    def statements: Seq[Stat] = before ++ Seq(mainClass, companion) ++ after
  }

  case class ClassForm(main: Defn.Class, before: Seq[Stat] = Seq.empty, after: Seq[Stat] = Seq.empty)
    extends GeneratedForm {
    def statements: Seq[Stat] = before ++ Seq(main) ++ after
  }

  case class ObjectForm(main: Defn.Object, before: Seq[Stat] = Seq.empty, after: Seq[Stat] = Seq.empty)
    extends GeneratedForm {
    def statements: Seq[Stat] = before ++ Seq(main) ++ after
  }

  case class SimpleForm(main: Seq[Stat], before: Seq[Stat] = Seq.empty, after: Seq[Stat] = Seq.empty)
    extends GeneratedForm {
    def statements: Seq[Stat] = before ++ main ++ after
  }

  case object EmptyForm extends GeneratedForm {
    def statements: Seq[Stat] = Seq.empty
  }
}
