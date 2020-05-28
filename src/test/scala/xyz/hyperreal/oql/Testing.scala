package xyz.hyperreal.oql

object Testing {

  val studentDB =
    new RDBConnection(
      """
        |student
        | id: integer, pk  name: text
        | 1                John
        | 2                Debbie
        |
        |class
        | id: integer, pk      name: text
        | 1                English
        | 2                Maths
        | 3                Spanish
        | 4                Biology
        | 5                Science
        | 6                Programming
        | 7                Law
        | 8                Commerce
        | 9                Physical Education
        |
        |enrollment
        | studentid: integer, fk, student, id  classid: integer, fk, class, id
        | 1                                    3
        | 1                                    5
        | 1                                    9
        | 2                                    1
        | 2                                    4
        | 2                                    5
        | 2                                    9
        |""".stripMargin
    )
  val studentER =
    new OQL(
      """
        |entity class {
        | *id: integer
        |  name: text
        |  students: [student] (enrollment)
        |}
        |
        |entity student {
        | *id: integer
        |  name: text
        |  classes: [class] (enrollment)
        |}
        |
        |entity enrollment {
        |  student (studentid): student
        |  class (classid): class
        |}
        |""".stripMargin
    )
  val starTrekDB =
    new RDBConnection(
      """
        |planet
        | plan_id: integer, pk  name: text   climate: text  
        | 1                     Earth       not too bad     
        | 2                     Vulcan      pretty hot      
        | 3                     Betazed     awesome weather 
        | 4                     Qo'noS      turbulent       
        | 5                     Turkana IV  null            
        |
        |species
        | spec_id: integer, pk  name: text  lifespan: integer  origin: integer, fk, planet, plan_id 
        | 1                     Human       71                 1                                    
        | 2                     Vulcan      220                2                                    
        | 3                     Betazoid    120                3                                    
        | 4                     Klingon     150                4                                    
        |
        |character
        | char_id: integer, pk      name: text       home: integer, fk, planet, plan_id  species: integer, fk, species, spec_id 
        | 1                     James Tiberius Kirk  1                                   1                                      
        | 2                     Spock                1                                   2                                      
        | 3                     Deanna Troi          1                                   3                                      
        | 4                     Worf, Son of Mogh    1                                   4                                      
        | 5                     Kurn, Son of Mogh    4                                   4                                      
        | 6                     Lwaxana Troi         3                                   3                                      
        | 7                     Natasha Yar          5                                   1
        |""".stripMargin
    )
  val starTrekER =
    new OQL(
      """
      |entity planet {
      | *plan_id: integer
      |  name: text
      |  climate: text
      |}
      |
      |entity species {
      | *spec_id: integer
      |  name: text
      |  lifespan: integer
      |  origin: planet
      |}
      |
      |entity character {
      | *char_id: integer
      |  name: text
      |  home: planet
      |  species: species
      |}
      |""".stripMargin
    )

}
