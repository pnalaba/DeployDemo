

enablePlugins(JavaAppPackaging)
enablePlugins(LinuxPlugin)

trapExit := false

//logLevel := Level.Error

ensimeScalaVersion in ThisBuild := "2.11.8"
lazy val root = (project in file(".")).
  settings(
    version := "0.1",
    organization := "",
    scalaVersion := "2.11.8",
    name := "streamdemo",
    resolvers ++= Seq(
      "MapR Repository" at "http://repository.mapr.com/maven/",
      "Spray Repository" at "http://repo.spray.io",
    ),
    libraryDependencies ++= Seq(
        "org.apache.spark" % "spark-core_2.11" % "2.3.1",
        "org.apache.spark" % "spark-mllib_2.11" % "2.3.1",
        "org.apache.spark" % "spark-sql_2.11" % "2.3.1",
        "org.apache.spark" % "spark-streaming_2.11" % "2.3.1",
        "com.typesafe.akka" %% "akka-actor" % "2.5.14",
        "com.typesafe.akka" %% "akka-http" % "10.1.3",
        "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.9",
        "com.typesafe.akka" %% "akka-cluster" % "2.5.14",
        "org.elasticsearch.client" % "elasticsearch-rest-high-level-client" % "6.6.1",
        "ch.megard" %% "akka-http-cors" % "0.2.2",
        "com.typesafe" % "config" % "1.3.2",
        "com.github.scopt" %% "scopt" % "3.7.0",
        "xerces" % "xercesImpl" % "2.11.0",
        "org.slf4j" % "slf4j-api" % "1.7.25",
        "com.typesafe.akka" %% "akka-slf4j" % "2.5.16",
    ),
    mainClass in(Compile, run) := Some("streamdemo.Boot"),
    mainClass in(Compile, packageBin) := Some("streamdemo.Boot"),
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


excludeFilter in unmanagedSources := HiddenFileFilter || "1_train.scala" || "2_score.scala"  || "3_stream.scala" || "4_data_separate_train.scala" || "5_data_separate_score.scala" || "stream_kafka.scala" || "6_allmetrics.scala"
