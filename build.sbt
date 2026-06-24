ThisBuild / scalaVersion := "2.13.12"

val akkaVersion     = "2.8.5"
val akkaHttpVersion = "10.5.3"
val circeVersion    = "0.14.6"

lazy val root = (project in file("."))
  .settings(
    name := "Receipe-Generator",
    libraryDependencies ++= Seq(
      // Akka HTTP
      "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion,
      "com.typesafe.akka" %% "akka-stream"        % akkaVersion,
      "com.typesafe.akka" %% "akka-actor-typed"   % akkaVersion,
      // Circe JSON
      "io.circe" %% "circe-core"    % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,
      "io.circe" %% "circe-parser"  % circeVersion,
      // Config & Logging
      "com.typesafe" %  "config"           % "1.4.3",
      "ch.qos.logback" % "logback-classic" % "1.4.14"
    )
  )
