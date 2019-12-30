
enablePlugins(JavaAppPackaging)
enablePlugins(LinuxPlugin)

trapExit := false

//logLevel := Level.Error

lazy val root = (project in file(".")).
  settings(
    version := "0.1",
    organization := "",
    scalaVersion := "2.11.8",
    name := "scalademo-core",
    resolvers ++= Seq(
      "MapR Repository" at "http://repository.mapr.com/maven/",
      "Spray Repository" at "http://repo.spray.io",
    ),
    libraryDependencies ++= Seq(
        "org.apache.spark" % "spark-core_2.11" % "2.3.1" % "provided",
        "org.apache.spark" % "spark-mllib_2.11" % "2.3.1" % "provided",
        "org.apache.spark" % "spark-sql_2.11" % "2.3.1" % "provided",
        "com.typesafe.akka" %% "akka-actor" % "2.5.14",
        "com.typesafe.akka" %% "akka-http" % "10.1.3",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.9",
        "com.typesafe.akka" %% "akka-cluster" % "2.5.14",
        "ch.megard" %% "akka-http-cors" % "0.2.2",
        "com.typesafe" % "config" % "1.3.2",
        "com.github.scopt" %% "scopt" % "3.7.0",
        "org.slf4j" % "slf4j-api" % "1.7.25",
        "com.typesafe.akka" %% "akka-slf4j" % "2.5.16",
    ),
    mainClass in(Compile, run) := Some("scalademo.Boot"),
    mainClass in(Compile, packageBin) := Some("scalademo.Boot"),
    dependencyOverrides ++= Seq(
    )
  )

val meta = """META.INF(.)*""".r
assemblyMergeStrategy in assembly := {
  case PathList("javax", "servlet", xs @ _*) => MergeStrategy.first
  case PathList(ps @ _*) if ps.last endsWith ".html" => MergeStrategy.first
  case n if n.startsWith("reference.conf") => MergeStrategy.concat
  case n if n.endsWith(".conf") => MergeStrategy.concat
  case meta(_) => MergeStrategy.discard
  case x => MergeStrategy.first
}

excludeFilter in unmanagedSources := HiddenFileFilter || "scalaTests.scala"

