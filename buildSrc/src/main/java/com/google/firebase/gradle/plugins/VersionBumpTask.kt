package com.google.firebase.gradle.plugins

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.provideDelegate

/**
 * Bumps the `version` property of the specified [versionFile].
 *
 * Primarily utilized as a post-release clean up task in which we bump the versions of released
 * modules to be one patch higher than their currently released counterparts.
 *
 * @property versionFile A [File] that contains the `version` property. Defaults to the
 * `gradle.properties` file at the project's root.
 * @property newVersion A [ModuleVersion] of what to set the version to. Defaults to one patch
 * higher than the existing version.
 */
abstract class VersionBumpTask : DefaultTask() {
  @get:[Optional InputFile]
  abstract val versionFile: Property<File>

  @get:[Optional Input]
  abstract val newVersion: Property<ModuleVersion>

  init {
    configure()
  }

  @TaskAction
  fun build() {
    with(versionFile.get()) {
      readLines()
        .map { it.takeUnless { it.startsWith("version=") } ?: "version=${newVersion.get()}" }
        .also { writeText(it.joinToString("\n") + '\n') }
    }
  }

  fun configure() {
    versionFile.convention(project.file("gradle.properties"))
    newVersion.convention(computeNewVersion())
  }

  fun computeNewVersion(): ModuleVersion? {
    val version: String? by project

    return version?.let { ModuleVersion.fromStringOrNull(it)?.bump() }
  }
}
