name := "oql"

version := "0.1"

scalaVersion := "2.13.2"

scalacOptions ++= Seq( "-deprecation", "-feature", "-unchecked", "-language:postfixOps", "-language:implicitConversions", "-language:existentials" )

organization := "xyz.hyperreal"

Global / onChangedBuildSource := ReloadOnSourceChanges

resolvers += "Typesafe Repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Hyperreal Repository" at "https://dl.bintray.com/edadma/maven"

libraryDependencies ++= Seq(
	"org.scalatest" %% "scalatest" % "3.1.1" % "test",
	"org.scalacheck" %% "scalacheck" % "1.14.1" % "test"
)

libraryDependencies ++= Seq(
	"org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
)

//libraryDependencies ++= Seq(
//  "com.typesafe" % "config" % "1.3.4"
//)

libraryDependencies ++= Seq(
  "org.jline" % "jline" % "3.14.1"
)

libraryDependencies ++= {
	val akkaV = "2.5.25"
	Seq(
//		"com.typesafe.akka" %% "akka-actor"    % akkaV
//		"com.typesafe.akka" %% "akka-remote"   % akkaV,
//		"com.typesafe.akka" %% "akka-testkit"  % akkaV    % "test",
//		"org.specs2"        %% "specs2-core"   % "2.3.11" % "test"
	)
}

//libraryDependencies ++= {
//	val akka_http = "10.1.10"
//	Seq(
//		"com.typesafe.akka" %% "akka-http-core"       % akka_http,
//		"com.typesafe.akka" %% "akka-http"            % akka_http,
//		"com.typesafe.akka" %% "akka-http-testkit"    % akka_http,
//		"com.typesafe.akka" %% "akka-http-spray-json" % akka_http,
//		"com.typesafe.akka" %% "akka-http-jackson"    % akka_http
//	)
//}

libraryDependencies ++= Seq(
)

coverageExcludedPackages := ".*Main"

mainClass in (Compile, run) := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

mainClass in assembly := Some( "xyz.hyperreal." + name.value.replace('-', '_') + ".Main" )

assemblyJarName in assembly := name.value + "-" + version.value + ".jar"

publishMavenStyle := true

publishArtifact in Test := false

pomIncludeRepository := { _ => false }

licenses := Seq("ISC" -> url("https://opensource.org/licenses/ISC"))

homepage := Some(url("https://github.com/edadma/" + name.value))

pomExtra :=
  <scm>
    <url>git@github.com:edadma/{name.value}.git</url>
    <connection>scm:git:git@github.com:edadma/{name.value}.git</connection>
  </scm>
  <developers>
    <developer>
      <id>edadma</id>
      <name>Edward A. Maxedon, Sr.</name>
      <url>https://github.com/edadma</url>
    </developer>
  </developers>
