package com.vinctus.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class LogicTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "not/or" in {
    studentER.json("enrollment { class { name } } [NOT (semester = 'fall' OR semester = 'winter')] <class.name>") map {
      result =>
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

  /*
  SELECT sum(order.ord_amount),
  count(order.ord_amount),
  agent$order.agent_name
  FROM order
    LEFT OUTER JOIN agent AS agent$order ON order.agent_code = agent$order.agent_code
  WHERE order.ord_amount BETWEEN 3000 AND 4000
  GROUP BY agent$order.agent_name
  ORDER BY (agent$order.agent_name) ASC
   */

  "between" in {
    ordersER.json(
      "order { sum(ord_amount) count(ord_amount) agent.agent_name } [ord_amount between 3000 and 4000] (agent.agent_name) <agent.agent_name>") map {
      result =>
        result shouldBe
          """
          |[
          |  {
          |    "sum_ord_amount": 6500,
          |    "count_ord_amount": 2,
          |    "agent": {
          |      "agent_name": "Alford"
          |    }
          |  },
          |  {
          |    "sum_ord_amount": 4000,
          |    "count_ord_amount": 1,
          |    "agent": {
          |      "agent_name": "Ivan"
          |    }
          |  },
          |  {
          |    "sum_ord_amount": 7500,
          |    "count_ord_amount": 2,
          |    "agent": {
          |      "agent_name": "Mukesh"
          |    }
          |  },
          |  {
          |    "sum_ord_amount": 10500,
          |    "count_ord_amount": 3,
          |    "agent": {
          |      "agent_name": "Santakumar"
          |    }
          |  }
          |]
      """.trim.stripMargin
    }
  }

}
