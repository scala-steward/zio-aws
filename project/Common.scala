
import io.github.vigoo.zioaws.codegen.ZioAwsCodegenPlugin.autoImport._
import sbt._
import Keys._
import xerial.sbt.Sonatype._
import xerial.sbt.Sonatype.SonatypeKeys._

object Common extends AutoPlugin {

  object autoImport {
    val zioVersion = "1.0.1"
    val zioCatsInteropVersion = "2.1.4.0"
    val zioReactiveStreamsInteropVersion = "1.0.3.5"
    val catsEffectVersion = "2.1.4"

    val awsVersion = "2.14.3"
    val awsSubVersion = awsVersion.drop(awsVersion.indexOf('.') + 1)
    val http4sVersion = "0.21.7"
    val fs2Version = "2.2.2"

    val majorVersion = "2"
    val minorVersion = "1"
    val zioAwsVersion = s"$majorVersion.$awsSubVersion.$minorVersion"

    val scala212Version = "2.12.12"
    val scala213Version = "2.13.3"

    val scalacOptions212 = Seq("-Ypartial-unification", "-deprecation")
    val scalacOptions213 = Seq("-deprecation")
  }

  import autoImport._

  override val trigger = allRequirements
  override lazy val projectSettings =
    Seq(
      scalaVersion := scala213Version,
      crossScalaVersions := List(scala212Version, scala213Version),

      organization := "io.github.vigoo",
      version := zioAwsVersion,

      awsLibraryVersion := awsVersion,

      scalacOptions ++= (CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 12)) => scalacOptions212
        case Some((2, 13)) => scalacOptions213
        case _ => Nil
      }),

      // Publishing
      publishMavenStyle := true,
      licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
      publishTo := sonatypePublishTo.value,
      sonatypeProjectHosting := Some(GitHubHosting("vigoo", "zio-aws", "daniel.vigovszky@gmail.com")),
      developers := List(
        Developer(id = "vigoo", name = "Daniel Vigovszky", email = "daniel.vigovszky@gmail.com", url = url("https://vigoo.github.io"))
      ),

      credentials ++=
        (for {
          username <- Option(System.getenv().get("SONATYPE_USERNAME"))
          password <- Option(System.getenv().get("SONATYPE_PASSWORD"))
        } yield
          Credentials(
            "Sonatype Nexus Repository Manager",
            "oss.sonatype.org",
            username,
            password)).toSeq,

    )
}