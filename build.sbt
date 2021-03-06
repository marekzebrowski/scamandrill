import SonatypeKeys._
import sbt.Keys._

sonatypeSettings

name := "scamandrill"

organization := "com.github.dzsessona"

profileName := "com.github.dzsessona"

description := "Scala client for Mandrill api"

scalaVersion := "2.11.8"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

resolvers ++= Seq(
  "Mewe relaases" at "https://nexus.groupl.es/repository/maven-releases/",
  "Mewe snapshosts" at "https://nexus.groupl.es/repository/maven-snapshots/"
)

parallelExecution in Test := true

libraryDependencies ++= {
  val akkaV = "2.4.7"
  Seq(
    "io.spray"          %% "spray-json"       % "1.3.2",
    "com.typesafe.akka" %% "akka-actor"       % akkaV,
    "com.typesafe.akka" %% "akka-http-experimental" % "2.4.7",
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % "2.4.2",
    "com.typesafe"      % "config"            % "1.3.0",
    "org.slf4j"         % "slf4j-api"         % "1.7.14"
  ) ++ Seq(
    "org.specs2"        %%  "specs2"          % "2.3.13"    % "test",
    "org.scalatest"     %%  "scalatest"       % "2.1.6"     % "test->*",
    "com.typesafe.akka" %% "akka-testkit"     % akkaV % "test"
  )
}

publishArtifact in Test := false

publishMavenStyle := true

pomIncludeRepository := { _ => false }

publishTo <<= (version) { v =>
  val nexus = "https://nexus.groupl.es/"
  if (v.endsWith("-SNAPSHOT"))
    Some("snapshots" at nexus+"repository/maven-snapshots/")
  else
    Some("releases" at nexus+"repository/maven-releases/")
}

credentials += Credentials(Path.userHome / ".ivy2" / ".meweCredentials")

//pgpPublicRing := file("/Users/dzsessona/Documents/mykeys/diegopgp.asc")

pomExtra := (
  <url>http://github.com/dzsessona/scamandrill</url>
    <licenses>
      <license>
        <name>Apache License 2.0</name>
        <url>http://opensource.org/licenses/Apache-2.0</url>
      </license>
    </licenses>
    <scm>
      <connection>scm:git:github.com/dzsessona/scamandrill.git</connection>
      <developerConnection>scm:git:git@github.com:dzsessona/scamandrill.git</developerConnection>
      <url>github.com/dzsessona/scamandrill</url>
    </scm>
    <developers>
      <developer>
        <id>dzsessona</id>
        <name>Diego Zambelli Sessona</name>
        <url>https://www.linkedin.com/in/diegozambellisessona</url>
      </developer>
    </developers>
  )
