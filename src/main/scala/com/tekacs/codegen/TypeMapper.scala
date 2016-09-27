package com.tekacs.codegen

import com.tekacs.codegen.DatabaseOps.DBType
import com.tekacs.codegen.NameMapper.{NamedType, Namer}

import scala.collection.concurrent
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions

class TypeMapper[NM <: NameMapper](nameMapper: NM) {
  private val resolvedTypes = concurrent.TrieMap[DBType, NamedType[_]]()
  private val promises = concurrent.TrieMap[DBType, Promise[Option[NamedType[_]]]]()
  private var isDone = false

  def beforeDone(): Boolean = true

  def done(): Unit = {
    if (!beforeDone()) return
    isDone = true
    promises.foreach {
      case (typ, promise) => promise success resolvedTypes.get(typ)
    }
  }

  def request(typ: DBType): Future[Option[NamedType[_]]] = {
    promises.getOrElseUpdate(typ, {
      if (isDone) System.err.println("request", typ)
      Promise()
    }).future
  }

  def found(typ: DBType)(namedType: NamedType[_]): Unit = {
    if (isDone) return
    resolvedTypes += ((typ, namedType))
  }
  def autoFound[T <: DBType](typ: T)(implicit namer: Namer[T]): Unit = {
    if (isDone) return
    resolvedTypes += ((typ, namer.named(typ)))
  }
}
