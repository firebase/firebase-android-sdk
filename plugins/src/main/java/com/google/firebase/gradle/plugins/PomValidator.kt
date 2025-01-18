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
import com.google.firebase.gradle.plugins.services.GMavenService
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

/**
 * Ensures that pom dependencies are not accidentally downgraded.
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

  @get:ServiceReference("gmaven") abstract val gmaven: Property<GMavenService>

  @TaskAction
  fun run() {
    if (!gmaven.get().hasReleasedArtifact(groupId.get(), artifactId.get()))
      skipGradleTask("Library hasn't been released")

    val oldPom = gmaven.get().latestPom(groupId.get(), artifactId.get())
    val currentPom = PomElement.fromFile(pomFile.get().asFile)

    val oldDependencies = getMapOfDependencies(oldPom)
    val currentDependencies = getMapOfDependencies(currentPom)

    val diff =
      currentDependencies
        .map { DependencyDiff(it.key, oldDependencies.getOrDefault(it.key, it.value), it.value) }
        .filter { it.oldVersion > it.currentVersion }
        .joinToString("\n") {
          "Dependency on ${it.artifactId} has been degraded from ${it.oldVersion} to ${it.currentVersion}"
        }

    if (diff.isNotBlank()) {
      throw GradleException("Dependency version errors found:\n${diff}")
    }
  }

  private fun getMapOfDependencies(pom: PomElement) =
    pom.dependencies
      .orEmpty()
      .filter { it.artifactId !in IGNORED_DEPENDENCIES }
      .associate {
        if (it.version === null)
          throw IllegalStateException(
            "Pom dependency has a transitive version declared, which makes it extremely difficult to verify: ${it.artifactId}"
          )

        it.artifactId to it.version
      }
      .mapValues {
        ModuleVersion.fromStringOrNull(it.value)
          ?: throw RuntimeException("Invalid module version found for '${it.key}': ${it.value}")
      }

  companion object {
    val IGNORED_DEPENDENCIES =
      listOf(
        "javax.inject", // javax.inject doesn't respect SemVer and doesn't update
        "dagger", // dagger doesn't respect Semver
        "listenablefuture", // guava's listenable future doesn't respect Semver
        "auto-service-annotations", // auto-service-annotations doesn't respect SemVer
      )
  }
}

private data class DependencyDiff(
  val artifactId: String,
  val oldVersion: ModuleVersion,
  val currentVersion: ModuleVersion,
)
