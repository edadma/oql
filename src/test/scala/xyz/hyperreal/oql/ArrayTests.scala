package xyz.hyperreal.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class ArrayTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "one-to-many/many-to-one" in {
    ordersER.json("agent { agent_code orders { ord_num customer.name } } [agent_code = 'A003']", ordersDB) map {
      result =>
        result shouldBe
          """
            |[
            |  {
            |    "agent_code": "A003",
            |    "orders": [
            |      {
            |        "ord_num": 200127,
            |        "customer": {
            |          "name": "C00015 asdf"
            |        }
            |      },
            |      {
            |        "ord_num": 200100,
            |        "customer": {
            |          "name": "C00015 asdf"
            |        }
            |      }
            |    ]
            |  }
            |]
      """.trim.stripMargin
    }
  }

}
