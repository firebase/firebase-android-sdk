/*
 * Copyright 2025 Google LLC
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

import com.google.firebase.gradle.plugins.semver.VersionDelta
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

abstract class SemVerTask @Inject constructor(private val execOperations: ExecOperations) :
  DefaultTask() {
  @get:InputFile abstract val apiTxtFile: RegularFileProperty
  @get:InputFile abstract val otherApiFile: RegularFileProperty
  @get:Input abstract val currentVersionString: Property<String>
  @get:Input abstract val previousVersionString: Property<String>

  // TODO cache output

  @TaskAction
  fun run() {
    val previous = ModuleVersion.fromStringOrNull(previousVersionString.get()) ?: return
    val current = ModuleVersion.fromStringOrNull(currentVersionString.get()) ?: return

    val bump =
      when {
        previous.major != current.major -> VersionDelta.MAJOR
        previous.minor != current.minor -> VersionDelta.MINOR
        else -> VersionDelta.PATCH
      }
    val stream = ByteArrayOutputStream()
    project.runMetalavaWithArgs(
      execOperations,
      listOf(
        "--source-files",
        apiTxtFile.get().asFile.absolutePath,
        "--check-compatibility:api:released",
        otherApiFile.get().asFile.absolutePath,
      ) +
        MAJOR.flatMap { m -> listOf("--error", m) } +
        MINOR.flatMap { m -> listOf("--error", m) } +
        IGNORED.flatMap { m -> listOf("--hide", m) } +
        listOf("--format=v3", "--no-color"),
      ignoreFailure = true,
      stdOut = stream,
    )

    val string = String(stream.toByteArray())
    val reg = Regex("(.*)\\s+error:\\s+(.*\\s+\\[(.*)\\])")
    val minorChanges = mutableListOf<String>()
    val majorChanges = mutableListOf<String>()
    for (match in reg.findAll(string)) {
      val message = match.groups[2]!!.value
      val type = match.groups[3]!!.value
      if (IGNORED.contains(type)) {
        continue // Shouldn't be possible
      } else if (MINOR.contains(type)) {
        minorChanges.add(message)
      } else {
        majorChanges.add(message)
      }
    }
    val allChanges =
      (majorChanges.joinToString(separator = "") { m -> "  MAJOR: $m\n" }) +
        minorChanges.joinToString(separator = "") { m -> "  MINOR: $m\n" }
    if (majorChanges.isNotEmpty()) {
      if (bump != VersionDelta.MAJOR) {
        throw GradleException(
          "API has non-bumped breaking MAJOR changes\nCurrent version bump is ${bump}, update the gradle.properties or fix the changes\n$allChanges"
        )
      }
    } else if (minorChanges.isNotEmpty()) {
      if (bump != VersionDelta.MAJOR && bump != VersionDelta.MINOR) {
        throw GradleException(
          "API has non-bumped MINOR changes\nCurrent version bump is ${bump}, update the gradle.properties or fix the changes\n$allChanges"
        )
      }
    }
  }

  companion object {
    private val MAJOR = setOf("AddedFinal")
    private val MINOR = setOf("AddedClass", "AddedMethod", "AddedField", "ChangedDeprecated")
    private val IGNORED = setOf("ReferencesDeprecated")
  }
}
