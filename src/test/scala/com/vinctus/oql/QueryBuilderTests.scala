package com.vinctus.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class QueryBuilderTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "deep many-to-one selection" in {
    starTrekER.queryBuilder.query("character").select("species.origin.name = 'Vulcan'").json map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "char_id": 2,
          |    "name": "Spock",
          |    "home": {
          |      "plan_id": 1,
          |      "name": "Earth",
          |      "climate": "not too bad"
          |    },
          |    "species": {
          |      "spec_id": 2,
          |      "name": "Vulcan",
          |      "lifespan": 220,
          |      "origin": {
          |        "plan_id": 2,
          |        "name": "Vulcan",
          |        "climate": "pretty hot"
          |      }
          |    }
          |  }
          |]
      """.trim.stripMargin
    }
  }

  "ordered resource selection" in {
    starTrekER.queryBuilder
      .query("character")
      .select("char_id < 4")
      .order("name", "ASC")
      .json map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "char_id": 3,
          |    "name": "Deanna Troi",
          |    "home": {
          |      "plan_id": 1,
          |      "name": "Earth",
          |      "climate": "not too bad"
          |    },
          |    "species": {
          |      "spec_id": 3,
          |      "name": "Betazoid",
          |      "lifespan": 120,
          |      "origin": {
          |        "plan_id": 3,
          |        "name": "Betazed",
          |        "climate": "awesome weather"
          |      }
          |    }
          |  },
          |  {
          |    "char_id": 1,
          |    "name": "James Tiberius Kirk",
          |    "home": {
          |      "plan_id": 1,
          |      "name": "Earth",
          |      "climate": "not too bad"
          |    },
          |    "species": {
          |      "spec_id": 1,
          |      "name": "Human",
          |      "lifespan": 71,
          |      "origin": {
          |        "plan_id": 1,
          |        "name": "Earth",
          |        "climate": "not too bad"
          |      }
          |    }
          |  },
          |  {
          |    "char_id": 2,
          |    "name": "Spock",
          |    "home": {
          |      "plan_id": 1,
          |      "name": "Earth",
          |      "climate": "not too bad"
          |    },
          |    "species": {
          |      "spec_id": 2,
          |      "name": "Vulcan",
          |      "lifespan": 220,
          |      "origin": {
          |        "plan_id": 2,
          |        "name": "Vulcan",
          |        "climate": "pretty hot"
          |      }
          |    }
          |  }
          |]
      """.trim.stripMargin
    }
  }

}
