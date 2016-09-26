package com.tekacs.codegen

import caseapp.CaseApp
import org.scalatest.FunSuite

import scala.meta._

class CodegenTest extends FunSuite {

  def structure(code: String): String = {
    import scala.meta._
    code.parse[Source].get.structure
  }

  test("--type-map") {
    val obtained =
      CaseApp.parse[CodegenOptions](
        Seq("--type-map", "numeric,BigDecimal;int8,Long"))
    val expected = Right(
      (CodegenOptions(
         typeMap = TypeMap("numeric" -> t"BigDecimal", "int8" -> t"Long")),
       Seq.empty[String]))
    assert(obtained === expected)
  }
}
