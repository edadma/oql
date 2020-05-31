package xyz.hyperreal.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class ArrayTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "one-to-many/many-to-one" in {
    ordersER.json("agent { agent_code orders { ord_num customer.name } <ord_num> } [agent_code = 'A003']", ordersDB) map {
      result =>
        result shouldBe
          """
            |[
            |  {
            |    "agent_code": "A003",
            |    "orders": [
            |      {
            |        "ord_num": 200100,
            |        "customer": {
            |          "name": "C00015 asdf"
            |        }
            |      },
            |      {
            |        "ord_num": 200127,
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

  "many-to-one" in {
    studentER.json("enrollment { student.name class.name grade } <grade> |2, 3|", studentDB) map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "student": {
          |      "name": "John"
          |    },
          |    "class": {
          |      "name": "Spanish"
          |    },
          |    "grade": "B+"
          |  },
          |  {
          |    "student": {
          |      "name": "Debbie"
          |    },
          |    "class": {
          |      "name": "Physical Education"
          |    },
          |    "grade": "B+"
          |  }
          |]
      """.trim.stripMargin
    }
  }

  "many-to-many" in {
    studentER.json("class { name students { name } } [name ~ 'S%']", studentDB) map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "name": "Spanish",
          |    "students": [
          |      {
          |        "name": "John"
          |      }
          |    ]
          |  },
          |  {
          |    "name": "Science",
          |    "students": [
          |      {
          |        "name": "John"
          |      },
          |      {
          |        "name": "Debbie"
          |      }
          |    ]
          |  }
          |]
      """.trim.stripMargin
    }
  }

  "many-to-one self-join" in {
    employeesER.json("employee { emp_name manager.emp_name } <emp_name> |3|", employeesDB) map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "emp_name": "ADELYN",
          |    "manager": {
          |      "emp_name": "BLAZE"
          |    }
          |  },
          |  {
          |    "emp_name": "ADNRES",
          |    "manager": {
          |      "emp_name": "SCARLET"
          |    }
          |  },
          |  {
          |    "emp_name": "BLAZE",
          |    "manager": {
          |      "emp_name": "KAYLING"
          |    }
          |  }
          |]
      """.trim.stripMargin
    }
  }

}
