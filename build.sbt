name := "@vinctus/oql"

version := "0.1.0-alpha.37"

description := "Object Query Language"

scalaVersion := "2.13.2"

scalacOptions ++= Seq( "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions", "-language:existentials" )

organization := "com.vinctus"

Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Hyperreal Repository" at "https://dl.bintray.com/edadma/maven"

enablePlugins(ScalaJSPlugin)

enablePlugins(ScalablyTypedConverterPlugin)

//Test / scalaJSUseMainModuleInitializer := true
//
//Test / scalaJSUseTestModuleInitializer := false

jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv()

npmDependencies in Compile ++= Seq(
  "pg" -> "8.2.1",
  "@types/pg" -> "7.14.3"
)

libraryDependencies ++= Seq(
  "org.scalatest" %%% "scalatest" % "3.1.1" % "test",
  "xyz.hyperreal" %%% "rdb-sjs" % "0.1.0-alpha.24" % "test"
)

libraryDependencies ++= Seq(
	"org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.2",
)

//libraryDependencies ++= Seq(
//  "com.typesafe" % "config" % "1.3.4"
//)

libraryDependencies ++= Seq(
  "org.scala-js" %%% "scalajs-java-time" % "1.0.0"
)

mainClass in (Compile, run) := Some( "com.vinctus." + "oql" + ".Main" )

lazy val packageName = SettingKey[String]("packageName", "package name")

packageName := (name.value indexOf '/' match {case -1 => name.value case idx => name.value.substring(idx + 1)}).replace('-', '_')

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))

homepage := Some(url("https://github.com/vinctustech/" + packageName.value))

pomExtra :=
  <scm>
    <url>git@github.com:vinctustech/{packageName.value}.git</url>
    <connection>scm:git:git@github.com:vinctustech/{packageName.value}.git</connection>
  </scm>
  <developers>
    <developer>
      <id>edadma</id>
      <name>Edward A. Maxedon, Sr.</name>
      <url>https://github.com/edadma</url>
    </developer>
  </developers>
