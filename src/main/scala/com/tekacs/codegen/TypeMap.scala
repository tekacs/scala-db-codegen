package com.tekacs.codegen

import scala.util.control.NonFatal
import scala.meta._

import caseapp.core.ArgParser

object TypeMap {
  implicit val parser: ArgParser[TypeMap] =
    ArgParser.instance[TypeMap] { s =>
      try {
        val pairs = s.split(";").map { pair =>
          val from :: to :: Nil = pair.split(",", 2).toList
          from -> to.parse[Type].get
        }
        Right(TypeMap(pairs: _*))
      } catch {
        case NonFatal(e) =>
          Left(s"invalid typeMap $s. Expected format from1,to1;from2,to2")
      }
    }
  val default = TypeMap(
    "text" -> t"String",
    "float8" -> t"Double",
    "numeric" -> t"BigDecimal",
    "int4" -> t"Int",
    "int8" -> t"Long",
    "bool" -> t"Boolean",
    "varchar" -> t"String",
    "serial" -> t"Int",
    "bigserial" -> t"Long",
    "timestamp" -> t"java.util.Date"
  )
}

case class TypeMap(pairs: (String, Type)*)
