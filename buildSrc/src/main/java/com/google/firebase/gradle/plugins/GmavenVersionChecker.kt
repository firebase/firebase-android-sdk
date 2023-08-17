package com.google.firebase.gradle.plugins

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

abstract class GmavenVersionChecker : DefaultTask() {

  @get:Input abstract val groupId: Property<String>

  @get:Input abstract val artifactId: Property<String>

  @get:Input abstract val latestReleasedVersion: Property<String>

  @get:Input abstract val version: Property<String>

  @TaskAction
  fun run() {
    val mavenHelper = GmavenHelper(groupId.get(), artifactId.get())
    val latestMavenVersion = mavenHelper.getLatestReleasedVersion()
    // Either the Maven metadata does not exist, or the library hasn't been released
    if (latestMavenVersion.isEmpty()) {
      return
    }
    if (version.get() == latestReleasedVersion.get()) {
      throw GradleException(
        "Current and latest released versions from gradle.properties are the same (${version.get()})" +
          "\nLatest release should be ${latestMavenVersion}"
      )
    }
    if (latestMavenVersion == version.get()) {
      throw GradleException(
        "Current version from gradle.properties (${version.get()}) is already the latest release on GMaven"
      )
    } else if (mavenHelper.hasReleasedVersion(version.get())) {
      throw GradleException(
        "Current version from gradle.properties (${version.get()}) has already been released on GMaven" +
          "\nThe latest released version on GMaven ${latestMavenVersion}"
      )
    }
    if (latestMavenVersion != latestReleasedVersion.get()) {
      if (mavenHelper.hasReleasedVersion(latestReleasedVersion.get())) {
        throw GradleException(
          "Latest released version from gradle.properties (${latestReleasedVersion.get()}) is not the latest released on GMaven (${latestMavenVersion})"
        )
      } else {
        throw GradleException(
          "Latest released version from gradle.properties (${latestReleasedVersion.get()}) has not been released on GMaven" +
            "\nThe latest released version on GMaven ${latestMavenVersion}"
        )
      }
    }
  }
}
