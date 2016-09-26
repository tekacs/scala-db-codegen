lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishMavenStyle := true,
  publishArtifact := true,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  publishArtifact in Test := false,
  licenses := Seq(
    "MIT" -> url("http://www.opensource.org/licenses/mit-license.php")),
  homepage := Some(url("https://github.com/tekacs/scala-db-codegen")),
  autoAPIMappings := true,
  apiURL := Some(url("https://github.com/tekacs/scala-db-codegen")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/tekacs/scala-db-codegen"),
      "scm:git:git@github.com:tekacs/scala-db-codegen.git"
    )
  ),
  pomExtra :=
    <developers>
        <developer>
          <id>tekacs</id>
          <name>Amar Sood</name>
          <url>https://tekacs.com</url>
        </developer>
        <developer>
          <id>olafurpg</id>
          <name>Ólafur Páll Geirsson</name>
          <url>https://geirsson.com</url>
        </developer>
      </developers>
)

lazy val `db-codegen` =
  (project in file("."))
    .settings(packSettings)
    .settings(publishSettings)
    .settings(
      name := "scala-db-codegen",
      organization := "com.tekacs",
      scalaVersion := "2.11.8",
      version := com.tekacs.codegen.Versions.nightly,
      packMain := Map("scala-db-codegen" -> "com.tekacs.codegen.Codegen"),
      libraryDependencies ++= Seq(
        "com.geirsson" %% "scalafmt-core" % "0.3.0",
        "io.getquill" %% "quill-core" % "0.8.0",
        "com.h2database" % "h2" % "1.4.192",
        "org.postgresql" % "postgresql" % "9.4-1201-jdbc41",
        "com.github.alexarchambault" %% "case-app" % "1.0.0-RC3",
        "org.scalameta" %% "scalameta" % "1.1.0",
        "org.scalatest" %% "scalatest" % "3.0.0" % "test"
      )
    )
