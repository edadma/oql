package com.vinctus.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class LogicTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "not/or" in {
    studentER.json("enrollment { class { name } } [NOT (semester = 'fall' OR semester = 'winter')] <class.name>",
                   studentDB) map { result =>
      result shouldBe
        """
            |[
            |  {
            |    "class": {
            |      "name": "Physical Education"
            |    }
            |  },
            |  {
            |    "class": {
            |      "name": "Science"
            |    }
            |  }
            |]
      """.trim.stripMargin
    }
  }

}
