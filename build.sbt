import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.Locale

organization := "com.autoscout24"

name := "bluewhale"

version := Option(System.getenv("GO_PIPELINE_LABEL")).getOrElse("1.0-SNAPSHOT")
scalaVersion in ThisBuild := "2.11.8"
scalacOptions in ThisBuild ++= Seq("-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")

val awsVersion = "1.11.13"

val slickVersion = "2.0.2"
libraryDependencies ++= Seq(
  ws,
  filters,
  "com.autoscout24" %% "eventpublisher24" % "131",

  specs2 % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1" % Test,

  "com.amazonaws" % "aws-java-sdk-dynamodb" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-cloudwatch" % awsVersion,
  "com.amazonaws" % "aws-java-sdk-kinesis" % awsVersion,

  "com.pauldijou" %% "jwt-play-json" % "0.8.1"

)

javaOptions in Test ++= Seq(
  "-Dconfig.resource=test.conf",
  "-Dlogger.resource=as24local-logger.xml"
)

javaOptions in Runtime ++= Seq(
  "-Dconfig.resource=as24local.conf",
  "-Dlogger.resource=as24local-logger.xml"
)


buildInfoKeys ++= Seq[BuildInfoKey](
  BuildInfoKey.action("buildTime") {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z", new Locale("en"))
    val timeZone = ZoneId.of("CET") // central european time
    ZonedDateTime.now(timeZone).format(dateTimeFormatter)
  })

// needed for specs2
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

lazy val root = (project in file("."))
  .enablePlugins(SbtWeb, PlayScala, BuildInfoPlugin)
  .settings(
    cleanFiles <+= baseDirectory { _ / "logs" },
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "info")

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// Asset pipeline
pipelineStages := Seq(uglify, digest, gzip)

// Don't package API docs in dist
doc in Compile <<= target.map(_ / "none")
