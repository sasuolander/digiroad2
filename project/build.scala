import io.gatling.sbt.GatlingPlugin
import sbt._
import sbt.Keys.{unmanagedResourceDirectories, _}
import org.scalatra.sbt._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin.{MergeStrategy, PathList}
import org.scalatra.sbt.PluginKeys._

object Digiroad2Build extends Build {
  val Organization = "fi.liikennevirasto"
  val Digiroad2Name = "digiroad2"
  val Digiroad2GeoName = "digiroad2-geo"
  val Version = "0.1.0-SNAPSHOT"
  val ScalaVersion = "2.11.7"
  val ScalatraVersion = "2.6.3"
  val AwsSdkVersion = "2.17.148"

  // Get build id to check if executing in aws environment.
  val awsBuildId: String = scala.util.Properties.envOrElse("CODEBUILD_BUILD_ID", null)

  lazy val geoJar =awsBuildId match {
    case null => {
      Project (
        Digiroad2GeoName,
        file(Digiroad2GeoName),
        settings = Defaults.defaultSettings ++ Seq(
          organization := Organization,
          name := Digiroad2GeoName,
          version := Version,
          scalaVersion := ScalaVersion,
          resolvers += Classpaths.typesafeReleases,
          scalacOptions ++= Seq("-unchecked", "-feature"),
          libraryDependencies ++= Seq(
            "org.joda" % "joda-convert" % "2.0.1",
            "joda-time" % "joda-time" % "2.9.9",
            "com.typesafe.akka" %% "akka-actor" % "2.5.12",
            "javax.media" % "jai_core" % "1.1.3" from "https://repo.osgeo.org/repository/release/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
            "org.geotools" % "gt-graph" % "19.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/org/geotools/gt-graph/19.0/gt-graph-19.0.jar",
            "org.geotools" % "gt-main" % "19.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/org/geotools/gt-main/19.0/gt-main-19.0.jar",
            "org.geotools" % "gt-api" % "19.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/org/geotools/gt-api/19.0/gt-api-19.0.jar",
            "org.geotools" % "gt-referencing" % "19.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/org/geotools/gt-referencing/19.0/gt-referencing-19.0.jar",
            "org.geotools" % "gt-metadata" % "19.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/org/geotools/gt-metadata/19.0/gt-metadata-19.0.jar",
            "org.geotools" % "gt-opengis" % "19.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/org/geotools/gt-opengis/19.0/gt-opengis-19.0.jar",
            "jgridshift" % "jgridshift" % "1.0" from "https://repo.osgeo.org/repository/release/jgridshift/jgridshift/1.0/jgridshift-1.0.jar",
            "com.vividsolutions" % "jts-core" % "1.14.0" from "http://livibuild04.vally.local/nexus/repository/maven-public/com/vividsolutions/jts-core/1.14.0/jts-core-1.14.0.jar",
            "org.scalatest" % "scalatest_2.11" % "3.2.0-SNAP7" % "test"
          )
        )
      )
    }
    case _ => {
      Project (
        Digiroad2GeoName,
        file(Digiroad2GeoName),
        settings = Defaults.defaultSettings ++ Seq(
          organization := Organization,
          name := Digiroad2GeoName,
          version := Version,
          scalaVersion := ScalaVersion,
          resolvers += Classpaths.typesafeReleases,
          scalacOptions ++= Seq("-unchecked", "-feature"),
          libraryDependencies ++= Seq(
            "org.joda" % "joda-convert" % "2.0.1",
            "joda-time" % "joda-time" % "2.9.9",
            "com.typesafe.akka" %% "akka-actor" % "2.5.12",
            "javax.media" % "jai_core" % "1.1.3" from "https://repo.osgeo.org/repository/release/javax/media/jai_core/1.1.3/jai_core-1.1.3.jar",
            "jgridshift" % "jgridshift" % "1.0" from "https://repo.osgeo.org/repository/release/jgridshift/jgridshift/1.0/jgridshift-1.0.jar",
            "org.scalatest" % "scalatest_2.11" % "3.2.0-SNAP7" % "test"
          )
        )
      )
    }
  }

  val Digiroad2OracleName = "digiroad2-oracle"
  lazy val oracleJar = Project (
    Digiroad2OracleName,
    file(Digiroad2OracleName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2OracleName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.apache.commons" % "commons-lang3" % "3.2",
        "commons-codec" % "commons-codec" % "1.9",
        "com.jolbox" % "bonecp" % "0.8.0.RELEASE",
        "org.scalatest" % "scalatest_2.11" % "3.2.0-SNAP7" % "test",
        "com.typesafe.slick" %% "slick" % "3.0.0",
        "org.json4s"   %% "json4s-jackson" % "3.5.3",
        "org.scala-lang.modules"   %% "scala-parser-combinators" % "1.1.0",
        "org.joda" % "joda-convert" % "2.0.1",
        "joda-time" % "joda-time" % "2.9.9",
        "com.github.tototoshi" %% "slick-joda-mapper" % "2.2.0",
        "com.github.tototoshi" %% "scala-csv" % "1.3.5",
        "org.apache.httpcomponents" % "httpclient" % "4.5.5",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.mockito" % "mockito-core" % "2.18.3" % "test",
        "com.googlecode.flyway" % "flyway-core" % "2.3.1" % "test",
        "com.googlecode.flyway" % "flyway-core" % "2.3.1",
        "javax.mail" % "javax.mail-api" % "1.6.1",
        "com.sun.mail" % "javax.mail" % "1.6.1",
        "org.postgresql" % "postgresql" % "42.2.5",
        "net.postgis" % "postgis-jdbc" % "2.3.0",
        "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
        "net.spy" % "spymemcached" % "2.12.3",
        "software.amazon.awssdk" % "s3" % AwsSdkVersion,
        "software.amazon.awssdk" % "sso" % AwsSdkVersion
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf"
    )
  ) dependsOn(geoJar)

  val Digiroad2ApiName = "digiroad2-api-common"
  lazy val commonApiJar = Project (
    Digiroad2ApiName,
    file(Digiroad2ApiName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2ApiName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      //      parallelExecution in Test := false,
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.5.12",
        "com.typesafe.akka" %% "akka-slf4j" % "2.5.12",
        "org.apache.httpcomponents" % "httpclient" % "4.5.5",
        "org.scalatest" % "scalatest_2.11" % "3.2.0-SNAP7" % "compile,test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-swagger"  % "2.6.3",
        "org.mockito" % "mockito-core" % "2.18.3" % "test",
        "org.joda" % "joda-convert" % "2.0.1",
        "joda-time" % "joda-time" % "2.9.9",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.15.v20160210" % "compile",
        "org.eclipse.jetty" % "jetty-servlets" % "9.2.15.v20160210" % "compile",
        "org.eclipse.jetty" % "jetty-proxy" % "9.2.15.v20160210" % "compile",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf"
    )
  ) dependsOn(geoJar, oracleJar)

  val Digiroad2OTHApiName = "digiroad2-api-oth"
  lazy val othApiJar = Project (
    Digiroad2OTHApiName,
    file(Digiroad2OTHApiName),
    settings = Defaults.defaultSettings ++ Seq(
      organization := Organization,
      name := Digiroad2OTHApiName,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      //      parallelExecution in Test := false,
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.5.3",
        "org.json4s"   %% "json4s-native" % "3.5.2",
        "org.scala-lang.modules"   %% "scala-parser-combinators" % "1.1.0",
        "org.scalatest" % "scalatest_2.11" % "3.2.0-SNAP7" % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.mockito" % "mockito-core" % "2.18.3" % "test",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % "test",
        "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
        "commons-io" % "commons-io" % "2.6",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3",
        "org.scalatra" %% "scalatra-swagger"  % "2.6.3"
      ),
      unmanagedResourceDirectories in Compile += baseDirectory.value / ".." / "conf"
    )
  ) dependsOn(geoJar, oracleJar, commonApiJar % "compile->compile;test->test")

  lazy val warProject = Project (
    Digiroad2Name,
    file("."),
    settings = Defaults.defaultSettings
      ++ assemblySettings
      ++ net.virtualvoid.sbt.graph.Plugin.graphSettings
      ++ ScalatraPlugin.scalatraWithJRebel ++ Seq(
      organization := Organization,
      name := Digiroad2Name,
      version := Version,
      scalaVersion := ScalaVersion,
      resolvers += Classpaths.typesafeReleases,
      scalacOptions ++= Seq("-unchecked", "-feature"),
      parallelExecution in Test := false,
      fork in (Compile,run) := true,
      testOptions in Test ++= (
        if (System.getProperty("digiroad2.nodatabase", "false") == "true") Seq(Tests.Argument("-l"), Tests.Argument("db")) else Seq()),
      libraryDependencies ++= Seq(
        "org.scalatra" %% "scalatra" % ScalatraVersion,
        "org.scalatra" %% "scalatra-json" % ScalatraVersion,
        "org.json4s"   %% "json4s-jackson" % "3.5.3",
        "org.scala-lang.modules"   %% "scala-parser-combinators" % "1.1.0",
        "org.scalatest" % "scalatest_2.11" % "3.2.0-SNAP7" % "test",
        "org.scalatra" %% "scalatra-scalatest" % ScalatraVersion % "test",
        "org.scalatra" %% "scalatra-auth" % ScalatraVersion,
        "org.scalatra" %% "scalatra-swagger"  % "2.6.3",
        "org.mockito" % "mockito-core" % "2.18.3" % "test",
        "com.typesafe.akka" %% "akka-testkit" % "2.5.12" % "test",
        "ch.qos.logback" % "logback-classic" % "1.2.3" % "runtime",
        "commons-io" % "commons-io" % "2.6",
        "com.newrelic.agent.java" % "newrelic-api" % "3.1.1",
        "org.apache.httpcomponents" % "httpclient" % "4.3.3",
        "org.eclipse.jetty" % "jetty-webapp" % "9.2.15.v20160210" % "container;compile",
        "org.eclipse.jetty" % "jetty-servlets" % "9.2.15.v20160210" % "container;compile",
        "org.eclipse.jetty" % "jetty-proxy" % "9.2.15.v20160210" % "container;compile",
        "org.eclipse.jetty" % "jetty-jmx" % "9.2.15.v20160210" % "container;compile",
        "org.eclipse.jetty.orbit" % "javax.servlet" % "3.0.0.v201112011016" % "container;provided;test" artifacts (Artifact("javax.servlet", "jar", "jar"))
      )
    )
  ) dependsOn(geoJar, oracleJar, commonApiJar, othApiJar) aggregate
    (geoJar, oracleJar, commonApiJar, othApiJar)

  lazy val gatling = project.in(file("digiroad2-gatling"))
    .enablePlugins(GatlingPlugin)
    .settings(scalaVersion := ScalaVersion)
    .settings(libraryDependencies ++= Seq(
    "io.gatling.highcharts" % "gatling-charts-highcharts" % "2.1.7" % "test",
    "io.gatling" % "gatling-test-framework" % "2.1.7" % "test"))

  val assemblySettings = sbtassembly.Plugin.assemblySettings ++ Seq(
    mainClass in assembly := Some("fi.liikennevirasto.digiroad2.ProductionServer"),
    test in assembly := {},
    mergeStrategy in assembly <<= (mergeStrategy in assembly) { old =>
    {
      case x if x.endsWith("about.html") => MergeStrategy.discard
      case x if x.endsWith("env.properties") => MergeStrategy.discard
      case x if x.endsWith("mime.types") => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.discard
      case PathList("META-INF", "maven", "com.fasterxml.jackson.core", "jackson-core", _*) => MergeStrategy.discard
      case x => old(x)
    } }
  )
}
