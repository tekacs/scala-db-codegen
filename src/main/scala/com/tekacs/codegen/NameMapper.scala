package com.tekacs.codegen

import com.tekacs.codegen.DatabaseOps._
import com.tekacs.codegen.Meta.HiddenImplicits._

import scala.language.implicitConversions
import scala.meta._

class NameMapper(ns: io.getquill.NamingStrategy) {
  import NameMapper._

  // To override in subclasses, provide your own object(s) and override these implicit defs.
  // e.g. `implicit override def tableNamer: Namer[TableName] = MyReplacementTableNamer`
  implicit def tableNamer: Namer[TableName] = TableNamer
  implicit def columnNamer: Namer[ColumnName] = ColumnNamer
  implicit def typeNamer: Namer[TypeName] = TypeNamer

  object TableNamer extends Namer[TableName] {
    override def className(tn: TableName): Type.Name = ns.table(tn.name)
    override def fieldName(tn: TableName): Term.Name = ns.column(tn.name)
    override def qualifiedClassName(tn: TableName): Type.Ref = className(tn)
  }

  object ColumnNamer extends Namer[ColumnName] {
    override def className(cn: ColumnName): Type.Name = ns.table(cn.column)
    override def fieldName(cn: ColumnName): Term.Name = ns.column(cn.column)
    override def qualifiedClassName(cn: ColumnName): Type.Ref =
      Type.Select(tableNamer.className(cn.table).value, className(cn))
  }

  object TypeNamer extends Namer[TypeName] {
    override def className(tn: TypeName): Type.Name = ns.table(tn.typ)
    override def fieldName(tn: TypeName): Term.Name = ns.column(tn.typ)
    override def qualifiedClassName(tn: TypeName): Type.Ref = className(tn)
  }

  object ExactNamer extends Namer[Type] {
    private def getName(typ: Type): String = typ match {
      case Type.Name(name) => name
      case Type.Select(_, name) => name.value
      case _ => ???
    }

    override def className(typ: Type): Type.Name = getName(typ)
    override def fieldName(typ: Type): Term.Name = getName(typ).toLowerCase
    override def qualifiedClassName(typ: Type): Type.Ref = typ.asInstanceOf[Type.Ref]
  }
}

object NameMapper {
  trait Namer[T] {
    def className(name: T): Type.Name
    def fieldName(name: T): Term.Name
    def qualifiedClassName(name: T): Type.Ref

    def named(name: T): NamedType[T] = new NamedType(name)(this)
  }

  def namerFor[T: Namer](name: T): Namer[T] = implicitly[Namer[T]]

  implicit class NamedType[T](val name: T)(implicit val namer: Namer[T]) extends Namer[T] {
    def className(name: T): Type.Name = namer.className(name)
    def fieldName(name: T): Term.Name = namer.fieldName(name)
    def qualifiedClassName(name: T): Type.Ref = namer.qualifiedClassName(name)

    def className: Type.Name = className(name)
    def fieldName: Term.Name = fieldName(name)
    def qualifiedClassName: Type.Ref = qualifiedClassName(name)
  }

  object NamedType {
    def unapply[T](nt: NamedType[T]): Option[T] = Some(nt.name)
  }
}
