// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.gradle

import com.google.common.collect.ImmutableList
import com.google.common.collect.Sets
import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

data class FirebaseLibrary(val moduleNames: List<String>, val directories: List<String>)

data class CommitDiff(
  val commitId: String,
  val author: String,
  val message: String,
) {
  override fun toString(): String =
    """
      |* ${message.split("\n").first()}   
      |  https://github.com/firebase/firebase-android-sdk/commit/${commitId}  [${author}]

    """
      .trimMargin()
}

abstract class ReleaseGenerator : DefaultTask() {

  @get:Input public abstract val currentRelease: Property<String>

  @get:Input public abstract val pastRelease: Property<String>

  @get:Input public abstract val printReleaseConfig: Property<String>

  @OutputFile public fun getReleaseConfigFile() = project.buildDir.resolve("release.cfg")

  @OutputFile public fun getReleaseReportFile() = project.buildDir.resolve("release_report.md")

  @TaskAction
  @Throws(Exception::class)
  fun generateReleaseConfig() {
    val rootDir = project.rootDir
    val availableModules = parseSubProjects(rootDir)

    val repo = Git.open(rootDir)
    val headRef = repo.repository.resolve(Constants.HEAD)
    val branchRef = getObjectRefForBranchName(repo, pastRelease.get())

    val libsToRelease = getChangedChangelogs(project, repo, branchRef, headRef, availableModules)
    val changedLibsWithNoChangelog =
      Sets.difference(
        getChangedLibraries(repo, branchRef, headRef, availableModules),
        libsToRelease.map { it.path }.toSet()
      )
    val changes = getChangesForLibraries(repo, branchRef, headRef, libsToRelease)
    writeReleaseConfig(
      getReleaseConfigFile(),
      ReleaseConfig(currentRelease.get(), libsToRelease.map { it.path }.toSet())
    )
    val releaseReport = generateReleaseReport(changes, changedLibsWithNoChangelog)
    if (printReleaseConfig.get().toBoolean()) {
      println(releaseReport)
    }
    writeReleaseReport(getReleaseReportFile(), releaseReport)
  }

  private fun generateReleaseReport(
    changes: Map<String, List<CommitDiff>>,
    changedLibrariesWithNoChangelog: Set<String>
  ) =
    """
      |# Release Report
      |${
            changes.entries.joinToString("\n") {
                """
      |## ${it.key}
      
      |${it.value.joinToString("\n") { it.toString() }}
      """.trimMargin()
            }
        }
      |
      |## SDKs with changes, but no changelogs
      |${changedLibrariesWithNoChangelog.joinToString("  \n")}
    """
      .trimMargin()

  private fun getChangesForLibraries(
    repo: Git,
    branchRef: ObjectId,
    headRef: ObjectId,
    changedLibraries: Set<Project>
  ) =
    changedLibraries
      .map { getRelativeDir(it) }
      .associateWith { getDirChanges(repo, branchRef, headRef, it) }
      .toMap()

  private fun extractLibraries(
    availableModules: Set<String>,
    rootDir: File
  ): List<FirebaseLibrary> {
    val nonKtxModules = availableModules.filter { !it.endsWith("ktx") }.toSet()
    return nonKtxModules
      .map { moduleName ->
        val ktxModuleName = "$moduleName:ktx"

        val moduleNames = listOf(moduleName, ktxModuleName).filter { availableModules.contains(it) }
        val directories = moduleNames.map { it.replace(":", "/") }

        FirebaseLibrary(moduleNames, directories)
      }
      .filter { firebaseLibrary ->
        firebaseLibrary.directories.first().let { File(rootDir, "$it/gradle.properties").exists() }
      }
  }

  private fun parseSubProjects(rootDir: File) =
    File(rootDir, "subprojects.cfg")
      .readLines()
      .filterNot { it.startsWith("#") || it.isEmpty() }
      .toSet()

  @Throws(GitAPIException::class)
  private fun getObjectRefForBranchName(repo: Git, branchName: String) =
    repo
      .branchList()
      .setListMode(ListBranchCommand.ListMode.REMOTE)
      .call()
      .firstOrNull { it.name == "refs/remotes/origin/releases/$branchName" }
      ?.objectId
      ?: throw RuntimeException("Could not find branch named $branchName")

  private fun getChangedLibraries(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    libraries: Set<String>
  ) =
    libraries
      .filter { library ->
        checkDirChanges(repo, previousReleaseRef, currentReleaseRef, "$library/")
      }
      .flatMap<String, String> {
        val temp =
          project.childProjects
            .get(it)!!
            .extensions
            .findByType(FirebaseLibraryExtension::class.java)
        if (temp == null) {
          return@flatMap ImmutableList.of<String>()
        }
        temp.projectsToRelease.map { it.path }
      }
      .toSet()

  private fun getChangedChangelogs(
    project: Project,
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    libraries: Set<String>
  ) =
    libraries
      .filter { library ->
        checkDirChanges(repo, previousReleaseRef, currentReleaseRef, "$library/CHANGELOG.md")
      }
      .flatMap {
        val childProject = project.childProjects.get(it)!!
        val extension = childProject.extensions.findByType(FirebaseLibraryExtension::class.java)
        if (extension == null) {
          return@flatMap ImmutableList.of(childProject)
        }
        extension.projectsToRelease
      }
      .toSet()

  private fun checkDirChanges(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    directory: String
  ) =
    repo
      .log()
      .addPath(directory)
      .addRange(previousReleaseRef, currentReleaseRef)
      .setMaxCount(1)
      .call()
      .iterator()
      .hasNext()

  private fun getDirChanges(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    directory: String
  ) =
    repo.log().addPath(directory).addRange(previousReleaseRef, currentReleaseRef).call().map {
      toCommitDiff(repo, it)
    }

  private fun toCommitDiff(repo: Git, revCommit: RevCommit): CommitDiff {
    return CommitDiff(
      revCommit.id.name,
      revCommit.authorIdent.name,
      revCommit.fullMessage,
    )
  }

  private fun writeReleaseReport(file: File, report: String) {
    file.writeText(report)
  }

  private fun writeReleaseConfig(file: File, config: ReleaseConfig) {
    file.writeText(config.toFile())
  }

  private fun getRelativeDir(project: Project) = project.path.substring(1).replace(':', '/')
}

data class ReleaseConfig(val releaseName: String, val libs: Set<String>) {
  companion object {
    fun fromFile(file: File): ReleaseConfig {
      val contents = file.readText(Charsets.UTF_8).split("\n")
      val libs = contents.filter { it.startsWith(":") }.toSet()
      val releaseName = contents.find { it.startsWith("name") }!!.split("=")[1].trim()
      return ReleaseConfig(releaseName, libs)
    }
  }

  fun toFile() =
    """
    |[release]
    |name = $releaseName
    |mode = RELEASE
                    
    |[modules]
    |${libs.sorted().joinToString("\n")}
    """
      .trimMargin()
}
