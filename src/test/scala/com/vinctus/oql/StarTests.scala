package com.vinctus.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class StarTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "aggregate" in {
    ordersER.json("agent { * -phone_no orders { sum(ord_amount) } } [working_area = 'Bangalore'] <agent_code>") map {
      result =>
        result shouldBe
          """
            |[
            |  {
            |    "agent_code": "A001",
            |    "agent_name": "Subbarao",
            |    "working_area": "Bangalore",
            |    "commission": 0.14,
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

  "recursion" in {
    studentER.json("student { * classes { * students <name> } <name> } [name = 'John']") map { result =>
      result shouldBe
        """
            |[
            |  {
            |    "id": 1,
            |    "name": "John",
            |    "classes": [
            |      {
            |        "id": 9,
            |        "name": "Physical Education",
            |        "students": [
            |          {
            |            "id": 2,
            |            "name": "Debbie"
            |          },
            |          {
            |            "id": 1,
            |            "name": "John"
            |          }
            |        ]
            |      },
            |      {
            |        "id": 5,
            |        "name": "Science",
            |        "students": [
            |          {
            |            "id": 2,
            |            "name": "Debbie"
            |          },
            |          {
            |            "id": 1,
            |            "name": "John"
            |          }
            |        ]
            |      },
            |      {
            |        "id": 3,
            |        "name": "Spanish",
            |        "students": [
            |          {
            |            "id": 1,
            |            "name": "John"
            |          }
            |        ]
            |      }
            |    ]
            |  }
            |]
           """.trim.stripMargin
    }
  }

}
