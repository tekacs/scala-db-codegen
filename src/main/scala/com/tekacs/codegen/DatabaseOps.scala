package com.tekacs.codegen

import java.sql.{Connection, ResultSet}

class DatabaseOps(val db: Connection, val schema: String, val excludedTables: Set[String]) {
  import DatabaseOps._

  lazy val foreignKeys: Set[ForeignKey] = {
    val foreignKeys = db.getMetaData.getExportedKeys(null, schema, null)
    results(foreignKeys).map { row =>
      ForeignKey(
        from = ColumnName(TableName(row.getString(FK_TABLE_NAME)), row.getString(FK_COLUMN_NAME)),
        to = ColumnName(TableName(row.getString(PK_TABLE_NAME)), row.getString(PK_COLUMN_NAME))
      )
    }.toSet
  }

  def tables: Seq[Table] = {
    val rs: ResultSet = db.getMetaData.getTables(null, schema, "%", Array("TABLE"))
    results(rs).flatMap { row =>
      val name = TableName(row.getString(TABLE_NAME))
      if (excludedTables.contains(name.name)) None
      else Some(Table(name, columns(name)))
    }.toVector
  }

  def columns(tableName: TableName): Seq[Column] = {
    val primaryKeySet = primaryKeys(tableName)
    val cols = db.getMetaData.getColumns(null, schema, tableName.name, null)
    results(cols).map { row =>
      val name = ColumnName(tableName, cols.getString(COLUMN_NAME))
      val isNullable = cols.getBoolean(NULLABLE)
      val isPrimaryKey = primaryKeySet contains cols.getString(COLUMN_NAME)

      val ref = foreignKeys.find(_.from == name).map(_.to)
      val typ = cols.getString(TYPE_NAME)

      Column(name, TypeName(typ), isNullable, isPrimaryKey, ref)
    }.toVector
  }

  def primaryKeys(tableName: TableName): Set[String] = {
    val resultSet = db.getMetaData.getPrimaryKeys(null, null, tableName.name)
    results(resultSet).map(_.getString(COLUMN_NAME)).toSet
  }
}

object DatabaseOps {
  val TABLE_NAME = "TABLE_NAME"
  val COLUMN_NAME = "COLUMN_NAME"
  val TYPE_NAME = "TYPE_NAME"
  val NULLABLE = "NULLABLE"
  val PK_NAME = "pk_name"
  val FK_TABLE_NAME = "fktable_name"
  val FK_COLUMN_NAME = "fkcolumn_name"
  val PK_TABLE_NAME = "pktable_name"
  val PK_COLUMN_NAME = "pkcolumn_name"

  def results(resultSet: ResultSet): Iterator[ResultSet] = {
    new Iterator[ResultSet] {
      def hasNext: Boolean = resultSet.next()
      def next(): ResultSet = resultSet
    }
  }

  case class ForeignKey(from: ColumnName, to: ColumnName)

  trait DBName
  case class TableName(name: String) extends DBName {
    override def toString: String = name
  }

  trait DBType extends DBName
  case class ColumnName(table: TableName, column: String) extends DBType {
    override def toString: String = s"$table.$column"
  }
  case class TypeName(typ: String) extends DBType {
    override def toString: String = typ
  }

  case class Column(name: ColumnName,
                    dbType: DBType,
                    isNullable: Boolean,
                    isPrimaryKey: Boolean,
                    ref: Option[ColumnName])

  case class Table(name: TableName,
                   columns: Seq[Column])
}

