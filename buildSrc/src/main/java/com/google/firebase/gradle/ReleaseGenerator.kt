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

import java.io.File
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.revwalk.RevCommit
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

data class FirebaseLibrary(val moduleNames: List<String>, val directories: List<String>)

data class CommitDiff(
  val commitId: String,
  val author: String,
  val message: String,
  val changes: List<String>
) {
  override fun toString(): String {
    return """
      |${message.split("\n")[0]}
      |    https://github.com/firebase/firebase-android-sdk/commit/${commitId}  [${author}]

    """
      .trimMargin()
  }
}

open class ReleaseGenerator : DefaultTask() {
  @TaskAction
  @Throws(Exception::class)
  fun generateReleaseConfig() {
    val currentRelease = project.property("currentRelease").toString()
    val pastRelease = project.property("pastRelease").toString()
    val printReleaseConfig = project.property("printOutput").toString().toBoolean()
    val rootDir = project.rootDir
    val availableModules = parseSubProjects(rootDir)
    val firebaseLibraries = extractLibraries(availableModules, rootDir)

    val repo = Git.open(rootDir)
    val headRef = repo.repository.resolve(Constants.HEAD)
    val branchRef = getObjectRefForBranchName(repo, pastRelease)

    val changedLibraries = getChangedLibraries(repo, branchRef, headRef, firebaseLibraries)
    val changes = getChangesForLibraries(repo, branchRef, headRef, changedLibraries)
    writeReleaseConfig(rootDir, changedLibraries, currentRelease)
    if (printReleaseConfig) {
      println(generatePrintOutput(changes))
    }
  }

  private fun generatePrintOutput(changes: Map<String, List<CommitDiff>>): String {
    return changes.entries.joinToString("\n") {
      """
      |### ${it.key}
      
      |  ${it.value.joinToString("\n  ") { it.toString() }}
    """
        .trimMargin()
    }
  }

  private fun getChangesForLibraries(
    repo: Git,
    branchRef: ObjectId,
    headRef: ObjectId,
    changedLibraries: List<String>
  ) =
    changedLibraries
      .map { it to getDirChanges(repo, branchRef, headRef, it) }
      .filterNot { it.second.isEmpty() }
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
      .firstOrNull { it.name == "refs/remotes/origin/$branchName" }
      ?.objectId
      ?: throw RuntimeException("Could not find branch named $branchName")

  private fun getChangedLibraries(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    libraries: List<FirebaseLibrary>
  ) =
    libraries
      .filter { library ->
        library.directories.any { checkDirChanges(repo, previousReleaseRef, currentReleaseRef, it) }
      }
      .flatMap { it.moduleNames }

  private fun checkDirChanges(
    repo: Git,
    previousReleaseRef: ObjectId,
    currentReleaseRef: ObjectId,
    directory: String
  ) =
    repo
      .log()
      .addPath("$directory/")
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
    repo
      .log()
      .addPath("$directory/")
      .addRange(previousReleaseRef, currentReleaseRef)
      .call()
      .map { toCommitDiff(repo, it) }
      .toList()

  private fun toCommitDiff(repo: Git, revCommit: RevCommit): CommitDiff {
    return CommitDiff(
      revCommit.id.name,
      revCommit.authorIdent.name,
      revCommit.fullMessage.toString(),
      emptyList()
    )
  }

  private fun writeReleaseConfig(configPath: File, libraries: List<String>, releaseName: String) {
    String to
      File(configPath, "release.cfg")
        .writeText(
          """
                    |[release]
                    |name = $releaseName
                    |mode = RELEASE
                    
                    |[modules]
                    |${libraries.joinToString("\n")}
                """
            .trimMargin()
        )
  }
}
