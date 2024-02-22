/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.gradle.plugins

import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Contains output data from the Release Generator, published as release_report.json
 *
 * @property changesByLibraryName contains libs which have opted into the release, and their changes
 * @property changedLibrariesWithNoChangelog contains libs not opted into the release, despite
 * having changes
 */
@Serializable
data class ReleaseReport(
  val changesByLibraryName: Map<String, List<CommitDiff>>,
  val changedLibrariesWithNoChangelog: Set<String>,
) {
  companion object {
    val formatter = Json { prettyPrint = true }

    fun fromFile(file: File) = formatter.decodeFromString<ReleaseReport>(file.readText())
  }

  fun toFile(file: File) = file.also { it.writeText(formatter.encodeToString(this)) }

  override fun toString() =
    """
      |# Release Report
      |${changesByLibraryName.entries.joinToString("\n") {
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
}

@Serializable
data class CommitDiff(
  val commitId: String,
  val prId: String,
  val author: String,
  val message: String,
  val commitLink: String,
  val prLink: String,
) {
  companion object {
    // This is a meant to capture the PR number from PR Titles
    // ex: "Fix a problem (#1234)" -> "1234"
    private val PR_ID_EXTRACTOR = Regex(".*\\(#(\\d+)\\).*")

    public fun fromRevCommit(commit: RevCommit): CommitDiff {
      val commitId = commit.id.name
      val prId =
        PR_ID_EXTRACTOR.find(commit.fullMessage.split("\n").first())?.groupValues?.get(1) ?: ""
      return CommitDiff(
        commitId,
        prId,
        commit.authorIdent.name,
        commit.fullMessage,
        "https://github.com/firebase/firebase-android-sdk/commit/$commitId",
        "https://github.com/firebase/firebase-android-sdk/pull/$prId",
      )
    }
  }

  override fun toString(): String =
    """
      |* ${message.split("\n").first()}   
      |  [pr]($prLink) [commit]($commitLink)  [$author]

    """
      .trimMargin()
}

abstract class ReleaseGenerator : DefaultTask() {
  companion object {
    private val RELEASE_CHANGE_FILTER = "NO_RELEASE_CHANGE"
  }

  @get:Input abstract val currentRelease: Property<String>

  @get:Input abstract val pastRelease: Property<String>

  @get:Optional @get:Input abstract val printReleaseConfig: Property<String>

  @get:Optional @get:InputFiles abstract val commitsToIgnoreFile: RegularFileProperty

  @get:Internal
  val commitsToIgnore: List<ObjectId>
    get() =
      commitsToIgnoreFile.asFileIfExistsOrNull()?.readLines()?.map { ObjectId.fromString(it) }
        ?: emptyList()

  @get:OutputFile abstract val releaseConfigFile: RegularFileProperty

  @get:OutputFile abstract val releaseReportJsonFile: RegularFileProperty

  @get:Internal lateinit var libraryGroups: Map<String, List<FirebaseLibraryExtension>>

  @TaskAction
  @Throws(Exception::class)
  fun generateReleaseConfig() {
    val rootDir = project.rootDir
    val availableModules = project.subprojects.filter { it.plugins.hasPlugin("firebase-library") }

    val repo = Git.open(rootDir)
    val headRef = repo.repository.resolve(Constants.HEAD)
    val branchRef = getObjectRefForBranchName(repo, pastRelease.get())

    val libsToRelease = getChangedChangelogs(repo, branchRef, headRef, availableModules)
    val changedLibsWithNoChangelog =
      getChangedLibraries(repo, branchRef, headRef, availableModules) subtract
        libsToRelease.map { it.path }.toSet()

    val changes = getChangesForLibraries(repo, branchRef, headRef, libsToRelease)

    val releaseConfig = ReleaseConfig(currentRelease.get(), libsToRelease.map { it.path })
    releaseConfig.toFile(releaseConfigFile.get().asFile)

    val releaseReport = ReleaseReport(changes, changedLibsWithNoChangelog)
    if (printReleaseConfig.orNull.toBoolean()) {
      project.logger.info(releaseReport.toString())
    }
    releaseReportJsonFile.get().asFile.let { releaseReport.toFile(it) }
  }

  private fun getChangesForLibraries(
    repo: Git,
    branchRef: ObjectId,
    headRef: ObjectId,
    changedLibraries: Set<Project>,
  ) =
    changedLibraries
      .map { getRelativeDir(it) }
      .associateWith { getDirChanges(repo, branchRef, headRef, it) }
      .toMap()

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
    libraries: List<Project>,
  ) =
    libraries
      .filter {
        checkDirChanges(repo, previousReleaseRef, currentReleaseRef, "${getRelativeDir(it)}/")
      }
      .flatMap { libraryGroups.getOrDefault(it.firebaseLibrary.libraryGroupName, emptyList()) }
      .map { it.path }
      .toSet()

  private fun getChangedChangelogs(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    libraries: List<Project>,
  ): Set<Project> =
    libraries
      .filter { library ->
        checkDirChanges(
          repo,
          previousReleaseRef,
          currentReleaseRef,
          "${getRelativeDir(library)}/CHANGELOG.md",
        )
      }
      .flatMap {
        libraryGroups.getOrDefault(it.firebaseLibrary.libraryGroupName, listOf(it.firebaseLibrary))
      }
      .map { it.project }
      .toSet()

  private fun checkDirChanges(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    directory: String,
  ) =
    repo
      .log()
      .addPath(directory)
      .addRange(previousReleaseRef, currentReleaseRef)
      .setMaxCount(10)
      .call()
      .filter {
        !it.fullMessage.contains(RELEASE_CHANGE_FILTER) &&
          !commitsToIgnore.any { ignore -> it.id == ignore }
      }
      .isNotEmpty()

  private fun getDirChanges(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    directory: String,
  ) =
    repo
      .log()
      .addPath(directory)
      .addRange(previousReleaseRef, currentReleaseRef)
      .call()
      .filter {
        !it.fullMessage.contains(RELEASE_CHANGE_FILTER) &&
          !commitsToIgnore.any { ignore -> it.id == ignore }
      }
      .map { CommitDiff.fromRevCommit(it) }

  private fun getRelativeDir(project: Project) = project.path.substring(1).replace(':', '/')
}

abstract class ReleaseReportGenerator : DefaultTask() {

  @get:InputFiles abstract val releaseReportJsonFile: RegularFileProperty

  @get:OutputFile abstract val releaseReportMdFile: RegularFileProperty

  @TaskAction
  @Throws(Exception::class)
  fun generateReleaseReport() {

    val releaseReport =
      ReleaseReport.fromFile(
        releaseReportJsonFile.asFileIfExistsOrNull()
          ?: throw RuntimeException("Missing release json file")
      )
    releaseReportMdFile.get().asFile.writeText(releaseReport.toString())
  }
}

fun RegularFileProperty.asFileIfExistsOrNull(): File? = orNull?.asFile?.takeIf { it.exists() }
