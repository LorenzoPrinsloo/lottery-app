// Latest LTS Version of Scala 3
val scala3Version = "3.3.6"

val catsVersion = "2.13.0"
val catsEffectVersion = "3.6.1"
val fs2Version = "3.12.0"
val http4sVersion = "0.23.30"
val circeVersion = "0.14.14"
val redis4catsVersion = "2.0.1"
val logbackVersion = "1.5.18"
val ip4sVersion = "3.7.0"
val log4catsVersion = "2.7.1"
val pureConfigVersion = "0.17.9"

ThisBuild / organization := "com.lottery"
ThisBuild / version      := "1.0.0"
ThisBuild / scalaVersion := "3.3.6"

lazy val commonDependencies = Seq(
  "org.scalameta" %% "munit" % "1.0.0" % Test,
  // --- Concurrency ---
  "org.typelevel" %% "cats-core" % catsVersion,
  "org.typelevel" %% "cats-effect" % catsEffectVersion,
  // --- Streaming ---
  "co.fs2" %% "fs2-core" % fs2Version,
  // --- HTTP ---
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  // --- JSON ---
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  // --- Redis ---
  "dev.profunktor" %% "redis4cats-core" % redis4catsVersion,
  "dev.profunktor" %% "redis4cats-effects" % redis4catsVersion,
  // --- Logging ---
  "dev.profunktor" %% "redis4cats-log4cats" % redis4catsVersion,
  "org.typelevel" %% "log4cats-slf4j" % log4catsVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion % Runtime,
  // --- Util ---
  "com.comcast" %% "ip4s-core" % ip4sVersion,
  // --- Config ---
  "com.github.pureconfig" %% "pureconfig-core" % pureConfigVersion,
  "com.github.pureconfig" % "pureconfig-ip4s_3" % pureConfigVersion,
  "com.github.pureconfig" %% "pureconfig-cats-effect" % pureConfigVersion
)

lazy val root = (project in file("."))
  .aggregate(core, lotteryApi, lotteryDraw)
  .settings(
    name := "lottery-root",
    publish / skip := true // This is an aggregator, don't publish it
  )

lazy val core = (project in file("core"))
  .settings(
    name := "lottery-core",
    scalaVersion := scala3Version,
    libraryDependencies ++= commonDependencies
  )

lazy val lotteryApi = (project in file("lottery-api"))
  .dependsOn(core)
  .enablePlugins(DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    name := "lottery-service", // This will be the name of the Docker image
//    libraryDependencies ++= commonDependencies,
    scalaVersion := scala3Version,
    mainClass := Some("com.lottery.api.Main"),
    dockerBaseImage := "eclipse-temurin:17-jre-alpine",
    dockerExposedPorts := Seq(8080),
    dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
    dockerRepository := None
  )

lazy val lotteryDraw = (project in file("lottery-draw"))
  .dependsOn(core)
  .enablePlugins(DockerPlugin, JavaAppPackaging, AshScriptPlugin)
  .settings(
    name := "draw-service", // This will be the name of the Docker image
    scalaVersion := scala3Version,
    mainClass := Some("com.lottery.draw.Main"),
    dockerBaseImage := "eclipse-temurin:17-jre-alpine",
    dockerExposedPorts := Seq(8080),
    dockerBuildOptions ++= Seq("--platform", "linux/amd64"),
    dockerRepository := None
  )
