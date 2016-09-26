package com.tekacs.codegen

import com.tekacs.codegen.Meta.HiddenImplicits._

import scala.language.implicitConversions
import scala.meta._

class Meta {
  def packageFor(name: Term.Ref, imports: Seq[Importer], body: Seq[Stat]): Pkg = {
    q"""package $name {
         import ..$imports

         ..$body
       }
     """.asInstanceOf[Pkg]
  }

  def objectFor(name: Term.Name, body: Seq[Stat], extending: Seq[Type] = Seq()): Defn.Object = {
    val ext = extending.map(_.ctorRef(Ctor.Name("never used")))
    q"object $name extends ..$ext { ..$body }".asInstanceOf[Defn.Object]
  }

  def classFor(name: Type.Name,
               params: Seq[Term.Param],
               body: Seq[Stat],
               extending: Seq[Type],
               mods: Seq[Mod] = Seq(mod"case")): Defn.Class = {
    val ext = extending.map(_.ctorRef(Ctor.Name("never used")))
    q"..$mods class $name(..$params) extends ..$ext { ..$body }".asInstanceOf[Defn.Class]
  }
}

object Meta {
  /** Contents of this object are re-imported at the top of the file.
    * They therefore aren't exported at the top level when ThisObject._ is imported elsewhere. */
  object HiddenImplicits {
    implicit def stringToTermName(s: String): Term.Name = Term.Name(s)
    implicit def stringToTypeName(s: String): Type.Name = Type.Name(s)
  }

  implicit class TypeUtils(typ: Type) {
    def name: Type.Name = typ match {
      case Type.Name(name) => name
      case Type.Select(_, name) => name.name
      case Type.Apply(t"Option" | t"Some", Seq(tpe)) => tpe.name
      case Type.Project(_, name) => name.name
      case Type.Singleton(term) => term.name.value
      case _ => ???
    }

    def constructor: Ctor.Call = typ.ctorRef(Ctor.fresh())

    def asOption: Type = t"Option[$typ]"
  }

  implicit class TermUtils(term: Term) {
    def name: Term.Name = term match {
      case Term.Name(name) => name
      case Term.Select(_, name) => name.name
      case Term.Apply(q"Some" | q"Option", Seq(term_)) => term_.name
      case Term.Param(_, name, _, _) => name.value
      case _ => ???
    }

    def asOption: Term = q"Some($term)".asInstanceOf[Term]
  }

  implicit def typeNameToTermName(typeName: Type.Name): Term.Name = typeName.value
  implicit def termNameToTypeName(termName: Term.Name): Type.Name = termName.value

  implicit class TermArgUtils(termArg: Term.Arg) {
    def name: Term.Name = termArg match {
      case Term.Arg.Named(name, _) => name.name
      case _ => ???
    }

    def asOption: Term.Arg = termArg match {
      case Term.Arg.Named(name, rhs) => Term.Arg.Named(name, q"Some($rhs)".asInstanceOf[Term])
      case _ => ???
    }
  }
}
