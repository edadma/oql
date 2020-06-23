package com.vinctus.oql

import org.scalatest._
import freespec.AsyncFreeSpec
import matchers.should.Matchers

import scala.concurrent.ExecutionContext

import Testing._

class BasicTests extends AsyncFreeSpec with Matchers {
  implicit override def executionContext: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  "query" in {
    starTrekER.queryMany("character { name species.origin.name } [species.name = 'Betazoid'] <name>", starTrekDB) map {
      result =>
        result shouldBe
          List(
            Map("name" -> "Deanna Troi", "species" -> Map("origin" -> Map("name" -> "Betazed"))),
            Map("name" -> "Lwaxana Troi", "species" -> Map("origin" -> Map("name" -> "Betazed")))
          )
    }
  }

  "findOne" in {
    studentER.findOne("class", 3, studentDB) map { result =>
      result shouldBe Some(Map("id" -> 3, "name" -> "Spanish"))
    }
  }

  "ordered" in {
    starTrekER.json("character <name>", starTrekDB) map { result =>
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
        |    "char_id": 5,
        |    "name": "Kurn, Son of Mogh",
        |    "home": {
        |      "plan_id": 4,
        |      "name": "Qo'noS",
        |      "climate": "turbulent"
        |    },
        |    "species": {
        |      "spec_id": 4,
        |      "name": "Klingon",
        |      "lifespan": 150,
        |      "origin": {
        |        "plan_id": 4,
        |        "name": "Qo'noS",
        |        "climate": "turbulent"
        |      }
        |    }
        |  },
        |  {
        |    "char_id": 6,
        |    "name": "Lwaxana Troi",
        |    "home": {
        |      "plan_id": 3,
        |      "name": "Betazed",
        |      "climate": "awesome weather"
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
        |    "char_id": 7,
        |    "name": "Natasha Yar",
        |    "home": {
        |      "plan_id": 5,
        |      "name": "Turkana IV",
        |      "climate": null
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
        |  },
        |  {
        |    "char_id": 4,
        |    "name": "Worf, Son of Mogh",
        |    "home": null,
        |    "species": {
        |      "spec_id": 4,
        |      "name": "Klingon",
        |      "lifespan": 150,
        |      "origin": {
        |        "plan_id": 4,
        |        "name": "Qo'noS",
        |        "climate": "turbulent"
        |      }
        |    }
        |  }
        |]
      """.trim.stripMargin
    }
  }

  "deep many-to-one selection" in {
    starTrekER.json("character [species.origin.name = 'Vulcan']", starTrekDB) map { result =>
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

  "lift" in {
    studentER.json("enrollment { ^student { * classes } } [&class = 9]", studentDB) map { result =>
      result shouldBe
        """
          |[
          |  {
          |    "id": 1,
          |    "name": "John",
          |    "classes": [
          |      {
          |        "id": 3,
          |        "name": "Spanish"
          |      },
          |      {
          |        "id": 5,
          |        "name": "Science"
          |      },
          |      {
          |        "id": 9,
          |        "name": "Physical Education"
          |      }
          |    ]
          |  },
          |  {
          |    "id": 2,
          |    "name": "Debbie",
          |    "classes": [
          |      {
          |        "id": 1,
          |        "name": "English"
          |      },
          |      {
          |        "id": 4,
          |        "name": "Biology"
          |      },
          |      {
          |        "id": 5,
          |        "name": "Science"
          |      },
          |      {
          |        "id": 9,
          |        "name": "Physical Education"
          |      }
          |    ]
          |  }
          |]
      """.trim.stripMargin
    }
  }

  "ordered resource selection" in {
    starTrekER.json("character [char_id < 4] <name>", starTrekDB) map { result =>
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

  "single selection" in {
    starTrekER.json("character [char_id = 3]", starTrekDB) map { result =>
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
          |  }
          |]
      """.trim.stripMargin
    }
  }

}
