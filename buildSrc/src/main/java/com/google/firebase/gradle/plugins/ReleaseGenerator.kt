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
package com.google.firebase.gradle.plugins

import com.google.common.collect.ImmutableList
import java.io.File
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
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.findByType

data class CommitDiff(
  val commitId: String,
  val author: String,
  val message: String,
) {
  constructor(
    revCommit: RevCommit
  ) : this(revCommit.id.name, revCommit.authorIdent.name, revCommit.fullMessage) {}

  override fun toString(): String =
    """
      |* ${message.split("\n").first()}   
      |  https://github.com/firebase/firebase-android-sdk/commit/${commitId}  [${author}]

    """
      .trimMargin()
}

abstract class ReleaseGenerator : DefaultTask() {

  @get:Input abstract val currentRelease: Property<String>

  @get:Input abstract val pastRelease: Property<String>

  @get:Optional @get:Input abstract val printReleaseConfig: Property<String>

  @get:OutputFile abstract val releaseConfigFile: RegularFileProperty

  @get:OutputFile abstract val releaseReportFile: RegularFileProperty

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

    val releaseReport = generateReleaseReport(changes, changedLibsWithNoChangelog)
    if (printReleaseConfig.orNull.toBoolean()) {
      project.logger.info(releaseReport)
    }
    writeReleaseReport(releaseReportFile.get().asFile, releaseReport)
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
    libraries: List<Project>
  ) =
    libraries
      .filter {
        checkDirChanges(repo, previousReleaseRef, currentReleaseRef, "${getRelativeDir(it)}/")
      }
      .flatMap {
        it.extensions.findByType<FirebaseLibraryExtension>()?.projectsToRelease?.map { it.path }
          ?: emptyList()
      }
      .toSet()

  private fun getChangedChangelogs(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    libraries: List<Project>
  ) =
    libraries
      .filter { library ->
        checkDirChanges(
          repo,
          previousReleaseRef,
          currentReleaseRef,
          "${getRelativeDir(library)}/CHANGELOG.md"
        )
      }
      .flatMap {
        it.extensions.findByType<FirebaseLibraryExtension>()?.projectsToRelease
          ?: ImmutableList.of(it)
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
      .setMaxCount(10)
      .call()
      .filter { !it.fullMessage.contains("NO_RELEASE_CHANGE") }
      .isNotEmpty()

  private fun getDirChanges(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    directory: String
  ) =
    repo.log().addPath(directory).addRange(previousReleaseRef, currentReleaseRef).call().map {
      CommitDiff(it)
    }

  private fun writeReleaseReport(file: File, report: String) = file.writeText(report)

  private fun getRelativeDir(project: Project) = project.path.substring(1).replace(':', '/')
}
