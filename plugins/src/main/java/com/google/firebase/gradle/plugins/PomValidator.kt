/*
 * Copyright 2023 Google LLC
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

import com.google.firebase.gradle.plugins.datamodels.PomElement
import com.google.firebase.gradle.plugins.datamodels.fullArtifactName
import com.google.firebase.gradle.plugins.datamodels.simpleDepString
import com.google.firebase.gradle.plugins.services.GMavenService
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element

/**
 * Ensures that pom dependencies are not accidently downgraded.
 *
 * Compares the latest pom at gmaven for the given artifact with the one generate for the current
 * release.
 *
 * @property pomFile The pom file for the current release
 * @property artifactId The artifactId for the pom parent
 * @property groupId The groupId for the pom parent
 * @throws GradleException if a dependency is found with a degraded version
 */
abstract class PomValidator : DefaultTask() {
  @get:InputFile abstract val pomFile: RegularFileProperty
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>

  @TaskAction
  fun run() {
    val pomUrl = getLatestReleasePomUrl() ?: return
    try {
      var diff = diffWithPomFromURL(pomUrl)

      if (diff.isNotEmpty()) {
        throw GradleException("Dependency version errors found:\n${diff}")
      }
    } catch (_: java.io.FileNotFoundException) {
      // Gmaven artifact doesn't exist.
      return
    }
  }

  private fun getLatestReleasePomUrl(): String? =
    with(GmavenHelper(groupId.get(), artifactId.get())) {
      val latestVersion = getLatestReleasedVersion()
      if (latestVersion.isEmpty()) null else getPomFileForVersion(latestVersion)
    }

  private fun getMapOfDependencies(doc: Element) =
    doc
      .findElementsByTag("dependency")
      .associate { it.textByTag("artifactId") to it.textByTag("version") }
      .filterKeys { it !in IGNORED_DEPENDENCIES }
      .mapValues {
        ModuleVersion.fromStringOrNull(it.value)
          ?: throw RuntimeException("Invalid module version found for '${it.key}': ${it.value}")
      }

  data class DependencyDiff(
    val artifactId: String,
    val oldVersion: ModuleVersion,
    val currentVersion: ModuleVersion,
  )

  fun diffWithPomFromURL(url: String): String {
    val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()

    val oldPom =
      URL(url)
        .openConnection()
        .apply {
          connectTimeout = 10000
          readTimeout = 10000
        }
        .getInputStream()
        .use { documentBuilder.parse(it) }
    val currentPom = documentBuilder.parse(pomFile.get().asFile)

    val oldDependencies = getMapOfDependencies(oldPom.documentElement)
    val currentDependencies = getMapOfDependencies(currentPom.documentElement)

    return currentDependencies
      .map { DependencyDiff(it.key, oldDependencies.getOrDefault(it.key, it.value), it.value) }
      .filter { it.oldVersion > it.currentVersion }
      .map {
        "Dependency on ${it.artifactId} has been degraded from ${it.oldVersion} to ${it.currentVersion}"
      }
      .joinToString("\n")
  }

  companion object {
    val IGNORED_DEPENDENCIES =
      listOf(
        "javax.inject", // javax.inject doesn't respect SemVer and doesn't update
        "dagger", // dagger doesn't respect Semver
        "listenablefuture", // guava's listenable future doesn't respect Semver
        "auto-service-annotations", // auto-service-annotations doesn't respect SemVer
        "re2j", //  re2j doesn't respect SemVer
      )
  }
}

/**
 * Checks if any dependencies have been added to or removed from the POM.
 *
 * Compares the latest POM at gmaven for the given artifact with the one generated for the current
 * release, printing any newly added or removed dependencies and failing the build if any are found.
 *
 * @property pomFile The pom file for the current release
 * @property artifactId The artifactId for the pom parent
 * @property groupId The groupId for the pom parent
 * @throws GradleException if a new or removed dependency is found in the current pom
 */
abstract class NewPomDependencyValidator : DefaultTask() {
  @get:InputFile abstract val pomFile: RegularFileProperty
  @get:Input abstract val artifactId: Property<String>
  @get:Input abstract val groupId: Property<String>
  @get:ServiceReference("gmaven") abstract val gmaven: Property<GMavenService>

  @TaskAction
  fun run() {
    val latestVersion = gmaven.get().latestVersionOrNull(groupId.get(), artifactId.get()) ?: return
    val oldPom = gmaven.get().pomOrNull(groupId.get(), artifactId.get(), latestVersion) ?: return
    val currentPom = PomElement.fromFile(pomFile.get().asFile)

    val diff = diffPoms(oldPom, currentPom)

    if (diff.isNotEmpty()) {
      println(diff)
      throw GradleException(diff)
    }
  }

  fun diffPoms(oldPom: PomElement, currentPom: PomElement): String {
    val oldDependencies =
      oldPom.dependencies.orEmpty().associate { it.fullArtifactName to it.simpleDepString }
    val currentDependencies =
      currentPom.dependencies.orEmpty().associate { it.fullArtifactName to it.simpleDepString }

    val newDependencies = currentDependencies.filterKeys { it !in oldDependencies }.values
    val removedDependencies = oldDependencies.filterKeys { it !in currentDependencies }.values

    return buildList {
        if (newDependencies.isNotEmpty()) {
          add(
            "New dependencies found, please verify they are intended:\n${newDependencies.joinToString("\n")}"
          )
        }
        if (removedDependencies.isNotEmpty()) {
          add(
            "Removed dependencies found, please verify they are intended:\n${removedDependencies.joinToString("\n")}"
          )
        }
      }
      .joinToString("\n\n")
  }
}
