package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.plugins.semver.VersionDelta
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream

abstract class SemVerTask : DefaultTask() {
  @get:InputFile
  abstract val apiTxtFile: RegularFileProperty
  @get:InputFile
  abstract val otherApiFile: RegularFileProperty
  @get:Input
  abstract val currentVersionString: Property<String>
  @get:Input
  abstract val previousVersionString: Property<String>

  @get:OutputFile
  abstract val outputApiFile: RegularFileProperty

  @TaskAction
  fun run() {
    val regex = Regex("\\d+\\.\\d+\\.\\d.*")
    if (!previousVersionString.get().matches(regex) || !currentVersionString.get().matches(regex)) {
      return // If these variables don't exist, no reason to check API
    }
    val (previousMajor, previousMinor, previousPatch) = previousVersionString.get().split(".")
    val (currentMajor, currentMinor, currentPatch) = currentVersionString.get().split(".")
    val bump = if (previousMajor != currentMajor) VersionDelta.MAJOR else if (previousMinor != currentMinor) VersionDelta.MINOR else VersionDelta.PATCH
    val stream = ByteArrayOutputStream()
    project.runMetalavaWithArgs(
      listOf(
        "--source-files",
        apiTxtFile.get().asFile.absolutePath,
        "--check-compatibility:api:released",
        otherApiFile.get().asFile.absolutePath,
      )
      + MAJOR.flatMap{ m -> listOf("--error", m) }
      + MINOR.flatMap{ m -> listOf("--error", m) }
      + IGNORED.flatMap{ m -> listOf("--hide", m) }
      + listOf(
        "--format=v3",
        "--no-color",
      ),
      ignoreFailure = true,
      stdOut = stream
    )

    val string = String(stream.toByteArray())
    val reg = Regex("(.*)\\s+error:\\s+(.*\\s+\\[(.*)\\])")
    val minorChanges = mutableListOf<String>()
    val majorChanges = mutableListOf<String>()
    for (match in reg.findAll(string)) {
      val loc = match.groups[1]!!.value
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
      (majorChanges
        .joinToString(separator = "") { m -> "  MAJOR: $m\n" }) +
      minorChanges
        .joinToString(separator = "")  { m -> "  MINOR: $m\n" }
    if (majorChanges.isNotEmpty()) {
      if (bump != VersionDelta.MAJOR) {
        throw GradleException("API has non-bumped breaking MAJOR changes\nCurrent version bump is ${bump}, update the gradle.properties or fix the changes\n$allChanges")
      }
    } else if (minorChanges.isNotEmpty()) {
      if (bump != VersionDelta.MAJOR && bump != VersionDelta.MINOR) {
        throw GradleException("API has non-bumped MINOR changes\nCurrent version bump is ${bump}, update the gradle.properties or fix the changes\n$allChanges")
      }
    }
  }
  companion object {
    private val MAJOR = setOf("AddedFinal")
    private val MINOR = setOf("AddedClass", "AddedMethod", "AddedField", "ChangedDeprecated")
    private val IGNORED = setOf("ReferencesDeprecated")
  }
}
