import com.typesafe.sbt.SbtGit.GitKeys
import com.typesafe.sbt.git.NullLogger
import sbt._
import sbt.Keys._
import de.knutwalker.sbt._
import de.knutwalker.sbt.KSbtKeys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.{ Vcs, Version }

object Build extends AutoPlugin {
  override def trigger = allRequirements
  override def requires = KSbtPlugin

  val akkaVersion = "2.3.14"

  object autoImport {
    lazy val genModules = taskKey[Seq[(File, String)]]("generate module files for guide")
    lazy val makeReadme = taskKey[Option[File]]("generate readme file from tutorial.")
    lazy val commitReadme = taskKey[Option[File]]("Commits the readme file.")
    lazy val buildReadmeContent = taskKey[Seq[(File, String)]]("Generate content for the readme file.")
    lazy val readmeFile = settingKey[File]("The readme file to build.")
    lazy val readmeCommitMessage = settingKey[String]("The message to commit the readme file with.")
  }
  import autoImport._

  override lazy val projectSettings = List(
         organization := "de.knutwalker",
            startYear := Some(2015),
           maintainer := "Paul Horn",
        githubProject := Github("knutwalker", "typed-actors"),
          description := "Compile time wrapper for more type safe actors",
         scalaVersion := "2.11.7",
  libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion % "provided",
          javaVersion := JavaVersion.Java17,
      autoAPIMappings := true,
         apiMappings ++= mapAkkaJar((externalDependencyClasspath in Compile).value, scalaBinaryVersion.value),
           genModules := generateModules(state.value, sourceManaged.value, streams.value.cacheDirectory, thisProject.value.dependencies),
           makeReadme := mkReadme(state.value, buildReadmeContent.?.value.getOrElse(Nil), readmeFile.?.value, readmeFile.?.value),
         commitReadme := addAndCommitReadme(state.value, makeReadme.value, readmeCommitMessage.?.value, releaseVcs.value),
             pomExtra := pomExtra.value ++
               <properties>
                 <info.apiURL>http://{githubProject.value.org}.github.io/{githubProject.value.repo}/api/{version.value}/</info.apiURL>
               </properties>,
    releaseProcess := List[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runClean,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishSignedArtifacts,
      releaseToCentral,
      pushGithubPages,
      commitTheReadme,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

  def mapAkkaJar(cp: Seq[Attributed[File]], crossVersion: String): Map[File, URL] =
    cp.collect {
      case file if file.data.toPath.endsWith(s"akka-actor_$crossVersion-$akkaVersion.jar") ⇒
        (file.data, url(s"http://doc.akka.io/api/akka/$akkaVersion/"))
    }.toMap

  def generateModules(state: State, dir: File, cacheDir: File, modules: Seq[ClasspathDep[ProjectRef]]): Seq[(File, String)] = {
    val files = new GenerateModulesTask(state, dir, cacheDir, modules.map(_.project)).apply()
    files.map(x ⇒ (x, x.getName))
  }

  def mkReadme(state: State, srcs: Seq[(File,String)], tpl: Option[File], out: Option[File]): Option[File] = {
    tpl.filter(_.exists()).flatMap { template ⇒
      out.flatMap {outputFile ⇒
        val sources = srcs.map(_._1)
        val extracted = Project.extract(state)
        val (_, latestVersion) = getLatestVersion(state, extracted)
        val tutLine = raw"tut: (\d+)".r
        val titleLine = raw"title: (.+)".r
        val directLink = raw".*\([^/]+.html\).*".r
        val files = sources.flatMap {f ⇒
          val lines = IO.readLines(f)
          val (front, content) = lines.dropWhile(_ == "---").span(_ != "---")
          for {
            index ← front.collectFirst {case tutLine(idx) ⇒ idx.toInt}
            title ← front.collectFirst {case titleLine(t) ⇒ t}
          } yield {
            val actualContent = content.drop(1).withFilter {
              case directLink() ⇒ false
              case _            ⇒ true
            }.map {line ⇒
              line.replaceAll(raw"\{\{ site\.data\.version\.version \}\}", latestVersion)
            }
            (index, title, actualContent)
          }
        }
        val lines = files.sortBy(_._1).flatMap(line ⇒ "" :: "## " + line._2 :: "" :: line._3)
        Some(lines).filter(_.nonEmpty).map { ls ⇒
          val targetLines = IO.readLines(template)
          val (head, middle) = targetLines.span(_ != "<!--- TUT:START -->")
          val (_, tail) = middle.span(_ != "<!--- TUT:END -->")
          IO.writeLines(outputFile, head)
          IO.writeLines(outputFile, middle.take(1), append = true)
          IO.writeLines(outputFile, ls, append = true)
          IO.writeLines(outputFile, tail, append = true)
          outputFile
        }
      }
    }
  }

  def addAndCommitReadme(state: State, readme: Option[File], message: Option[String], maybeVcs: Option[Vcs]): Option[File] = for {
    vcs ← maybeVcs
    file ← readme
    msg ← message
    relative ← IO.relativize(vcs.baseDir, file)
    ff ← tryCommit(msg, vcs, file, relative, state.log)
  } yield ff

  def tryCommit(message: String, vcs: Vcs, file: File, relative: String, log: Logger): Option[File] = {
    vcs.add(relative) !! log
    val status = vcs.status.!!.trim
    if (status.nonEmpty) {
      vcs.commit(message) ! log
      Some(file)
    } else {
      None
    }
  }

  def getLatestVersion(state: State, extracted: Extracted): (State, String) = {
    val baseDir = extracted.get(baseDirectory)
    val currentVersion = extracted.get(version)
    val (nextState, runner) = extracted.runTask(GitKeys.gitRunner, state)
    val tagDashEl = runner("tag", "-l")(baseDir, NullLogger)
    val tags = tagDashEl.trim.split("\\s+").toSeq.map(_.replaceFirst("^v", ""))
    val sortedTags = tags.flatMap(Version(_)).sorted.map(_.string)
    (nextState, sortedTags.lastOption.getOrElse(currentVersion))
  }

  private class GenerateModulesTask(state: State, dir: File, cacheDir: File, modules: Seq[ProjectRef]) {
    val tempModulesFile = cacheDir / "gen-modules" / "modules.yml"
    val tempVersionFile = cacheDir / "gen-modules" / "version.yml"
    val modulesFile = dir / "modules.yml"
    val versionFile = dir / "version.yml"

    def apply(): Seq[File] = {
      mkFiles()
      List(
        cachedCopyOf(tempVersionFile, versionFile),
        cachedCopyOf(tempModulesFile, modulesFile)
      )
    }

    def mkFiles() = {
      val extracted = Project.extract(state)
      val (_, latestVersion) = getLatestVersion(state, extracted)
      val lines = mkLines(extracted, latestVersion)
      IO.writeLines(tempModulesFile, lines)
      IO.writeLines(tempVersionFile, s"version: $latestVersion" :: Nil)
    }

    def cachedCopyOf(from: File, to: File): File = {
      val cacheFile = cacheDir / "gen-modules" / "cached-inputs" / from.getName
      val check = Tracked.inputChanged(cacheFile) {(hasChanged, input: HashFileInfo) ⇒
        if (hasChanged || !to.exists()) {
          IO.copyFile(from, to, preserveLastModified = true)
        }
      }
      check(FileInfo.hash(from))
      to
    }

    def mkLines(extracted: Extracted, latestVersion: String) =
      modules.flatMap { proj ⇒
        Seq(
          s"- organization: ${extracted.get(organization in proj)}",
          s"  name: ${extracted.get(name in proj)}",
          s"  version: $latestVersion"
        )
      }
  }

  implicit val versionOrdering = new Ordering[Version] {
    def compare(x: Version, y: Version): Int =
      x.major compare y.major match {
        case 0 ⇒ x.minor.getOrElse(0) compare y.minor.getOrElse(0) match {
          case 0 ⇒ x.bugfix.getOrElse(0) compare y.bugfix.getOrElse(0) match {
            case 0 ⇒ (x.qualifier, y.qualifier) match {
              case (None, None) ⇒ 0
              case (Some(_), Some(_)) ⇒ 0
              case (None, _) ⇒ 1
              case (_, None) ⇒ -1
            }
            case a ⇒ a
          }
          case a ⇒ a
        }
        case a ⇒ a
      }
  }

  private lazy val publishSignedArtifacts = ReleaseStep(
    action = Command.process("publishSigned", _),
    enableCrossBuild = true
  )

  private lazy val releaseToCentral = ReleaseStep(
    action = Command.process("sonatypeReleaseAll", _),
    enableCrossBuild = true
  )

  private lazy val pushGithubPages = ReleaseStep(
    action = Command.process("docs/ghpagesPushSite", _),
    enableCrossBuild = false
  )

  private lazy val commitTheReadme = ReleaseStep(
    action = Command.process("docs/commitReadme", _),
    enableCrossBuild = false
  )
}
