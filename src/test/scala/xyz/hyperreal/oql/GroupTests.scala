package xyz.hyperreal.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class GroupTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "grouped" in {
    studentER.json("enrollment { student { name count(name) } } [student.name = 'John'] (student.name) <student.name>",
                   studentDB) map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "student": {
          |      "name": "John",
          |      "count_name": 3
          |    }
          |  }
          |]
      """.trim.stripMargin
    }
  }

}
