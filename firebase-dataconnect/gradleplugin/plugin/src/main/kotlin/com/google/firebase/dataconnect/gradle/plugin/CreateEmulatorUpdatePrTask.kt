/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.gradle.plugin

import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

@Suppress("unused")
abstract class CreateEmulatorUpdatePrTask : DefaultTask() {

  @get:Input abstract val jsonFile: Property<File>

  @get:Internal abstract val workDirectory: DirectoryProperty

  @get:Inject abstract val execOperations: ExecOperations

  init {
    outputs.upToDateWhen { false }
  }

  @TaskAction
  fun run() {
    val jsonFile: File = jsonFile.get()
    val workDirectory: File = workDirectory.get().asFile

    // 1. Read Old JSON
    val oldRegistry = DataConnectExecutableVersionsRegistry.load(jsonFile)
    val oldDefault = oldRegistry.defaultVersion.toString()

    // 2. Update Registry in memory
    val newRegistry =
      UpdateDataConnectExecutableVersionsTask.updateRegistry(
        registry = oldRegistry,
        workDirectory = workDirectory,
        logger = logger,
      )

    if (newRegistry == null) {
      logger.lifecycle("No new emulator versions found. Aborting.")
      return
    }

    val newDefault = newRegistry.defaultVersion.toString()

    if (oldDefault == newDefault) {
      logger.lifecycle("Default version did not change ($oldDefault). Aborting.")
      return
    }

    val oldVersions = oldRegistry.versions.map { it.version.toString() }.toSet()
    val newVersions = newRegistry.versions.map { it.version.toString() }.toSet()
    val addedVersionsList = newVersions.subtract(oldVersions).sorted()
    val addedVersions =
      if (addedVersionsList.size > 1) {
        addedVersionsList.dropLast(1).joinToString(", ") +
          ", and " +
          addedVersionsList.last()
      } else {
        addedVersionsList.joinToString(", ")
      }

    logger.lifecycle("Default changed: $oldDefault -> $newDefault")
    logger.lifecycle("Added versions: $addedVersions")

    // 3. Save New JSON
    DataConnectExecutableVersionsRegistry.save(newRegistry, jsonFile)

    // 4. Create Branch
    val branchName = "Emulator_${newDefault.replace(".", "_")}"
    execOperations.exec { it.commandLine("git", "checkout", "-b", branchName) }

    // 5. Commit JSON
    val commitMsg1 =
      "DataConnectExecutableVersions.json: defaultVersion changed to \"$newDefault\" (was \"$oldDefault\") and added versions $addedVersions"
    execOperations.exec { it.commandLine("git", "add", jsonFile.absolutePath) }
    execOperations.exec { it.commandLine("git", "commit", "-m", commitMsg1) }

    // 6. Fetch latest firebase-tools version
    val ghOutput = ByteArrayOutputStream()
    execOperations.exec {
      it.commandLine(
        "gh",
        "release",
        "view",
        "--repo",
        "Firebase/firebase-tools",
        "--json",
        "tagName",
        "-q",
        ".tagName"
      )
      it.standardOutput = ghOutput
    }
    val latestToolsVersion = ghOutput.toString().trim().removePrefix("v")

    // 7. Update YAMLs
    val projectRootDir = project.rootProject.rootDir
    val dataconnectYaml = File(projectRootDir, ".github/workflows/dataconnect.yml")
    val dataconnectDemoYaml = File(projectRootDir, ".github/workflows/dataconnect_demo_app.yml")

    val regex =
      """FDC_FIREBASE_TOOLS_VERSION:\s*\$\{\{\s*inputs\.firebaseToolsVersion\s*\|\|\s*'([^']+)'\s*\}\}""".toRegex()
    var oldToolsVersion = "unknown"

    listOf(dataconnectYaml, dataconnectDemoYaml).forEach { file ->
      if (file.exists()) {
        var content = file.readText()
        val match = regex.find(content)
        if (match != null) {
          oldToolsVersion = match.groupValues[1]
          content = content.replace(oldToolsVersion, latestToolsVersion)
          file.writeText(content)
        } else {
          logger.warn("Could not find FDC_FIREBASE_TOOLS_VERSION in ${file.name}")
        }
      } else {
        logger.warn("File not found: ${file.absolutePath}")
      }
    }

    if (oldToolsVersion == "unknown") {
      logger.error("Could not determine old firebase-tools version. Aborting.")
      return
    }

    // 8. Commit YAMLs
    val commitMsg2 =
      "dataconnect.yml/dataconnect_demo_app.yml: updated firebase-tools to $latestToolsVersion (was $oldToolsVersion)"
    execOperations.exec {
      it.commandLine(
        "git",
        "add",
        dataconnectYaml.absolutePath,
        dataconnectDemoYaml.absolutePath
      )
    }
    execOperations.exec { it.commandLine("git", "commit", "-m", commitMsg2) }

    // 9. Push Branch
    execOperations.exec {
      it.commandLine("git", "push", "-u", "origin", "$branchName:dataconnect/$branchName")
    }

    // 10. Create PR
    val prTitle =
      "dataconnect: ci: upgrade data connect emulator to $newDefault (was $oldDefault) and firebase-tools to $latestToolsVersion (was $oldToolsVersion)"
    execOperations.exec {
      it.commandLine(
        "gh",
        "pr",
        "create",
        "--body",
        "",
        "--title",
        prTitle,
        "--assignee",
        "@me",
        "--label",
        "api: dataconnect",
        "--label",
        "main-merge-ack",
        "--label",
        "no-changelog"
      )
    }

    logger.lifecycle("Successfully created PR!")
  }
}
