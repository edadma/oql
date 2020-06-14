package com.vinctus.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class StarTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "grouped" in {
    ordersER.json("agent { * orders { sum(ord_amount) } } [working_area = 'Bangalore'] <agent_code>", ordersDB) map {
      result =>
        result shouldBe
          """
            |[
            |  {
            |    "agent_code": "A001",
            |    "agent_name": "Subbarao",
            |    "working_area": "Bangalore",
            |    "commission": 0.14,
            |    "phone_no": "077-12346674",
            |    "orders": [
            |      {
            |        "sum_ord_amount": 800
            |      }
            |    ]
            |  },
            |  {
            |    "agent_code": "A007",
            |    "agent_name": "Ramasundar",
            |    "working_area": "Bangalore",
            |    "commission": 0.15,
            |    "phone_no": "077-25814763",
            |    "orders": [
            |      {
            |        "sum_ord_amount": 2500
            |      }
            |    ]
            |  },
            |  {
            |    "agent_code": "A011",
            |    "agent_name": "Ravi Kumar",
            |    "working_area": "Bangalore",
            |    "commission": 0.15,
            |    "phone_no": "077-45625874",
            |    "orders": [
            |      {
            |        "sum_ord_amount": 5000
            |      }
            |    ]
            |  }
            |]
           """.trim.stripMargin
    }
  }

}
