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

package com.google.firebase.gradle.bomgenerator

import com.google.firebase.gradle.plugins.ModuleVersion
import com.google.firebase.gradle.plugins.VersionType
import com.google.firebase.gradle.plugins.createIfAbsent
import com.google.firebase.gradle.plugins.datamodels.ArtifactDependency
import com.google.firebase.gradle.plugins.datamodels.DependencyManagementElement
import com.google.firebase.gradle.plugins.datamodels.LicenseElement
import com.google.firebase.gradle.plugins.datamodels.PomElement
import com.google.firebase.gradle.plugins.datamodels.fullArtifactName
import com.google.firebase.gradle.plugins.datamodels.moduleVersion
import com.google.firebase.gradle.plugins.orEmpty
import com.google.firebase.gradle.plugins.pairBy
import com.google.firebase.gradle.plugins.partitionNotNull
import com.google.firebase.gradle.plugins.services.GMavenService
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Generates the firebase bom, using gmaven as a source of truth for artifacts and versions.
 *
 * @see validateArtifacts
 * @see GenerateBomReleaseNotesTask
 * @see GenerateTutorialBundleTask
 */
abstract class GenerateBomTask : DefaultTask() {
  /**
   * Artifacts to include in the bom.
   *
   * ```
   * bomArtifacts.set(listOf(
   *   "com.google.firebase:firebase-firestore",
   *   "com.google.firebase:firebase-storage"
   * ))
   * ```
   */
  @get:Input abstract val bomArtifacts: ListProperty<String>

  /**
   * Artifacts to exclude from the bom.
   *
   * These are artifacts that are under the `com.google.firebase` namespace, but are intentionally
   * not included in the bom.
   *
   * ```
   * bomArtifacts.set(listOf(
   *   "com.google.firebase:crashlytics",
   *   "com.google.firebase:crash-plugin"
   * ))
   * ```
   */
  @get:Input abstract val ignoredArtifacts: ListProperty<String>

  /**
   * Optional map of versions to use instead of the versions on gmaven.
   *
   * ```
   * versionOverrides.set(mapOf(
   *   "com.google.firebase:firebase-firestore" to "10.0.0"
   * ))
   * ```
   */
  @get:Input abstract val versionOverrides: MapProperty<String, String>

  /** Directory to save the bom under. */
  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @get:ServiceReference("gmaven") abstract val gmaven: Property<GMavenService>

  @TaskAction
  fun generate() {
    val versionOverrides = versionOverrides.getOrElse(emptyMap())

    val validatedArtifactsToPublish = validateArtifacts()
    val artifactsToPublish =
      validatedArtifactsToPublish.map {
        val version = versionOverrides[it.fullArtifactName] ?: it.version
        logger.debug("Using ${it.fullArtifactName} with version $version")

        it.copy(version = version)
      }

    val newVersion = determineNewBomVersion(artifactsToPublish)

    val pom =
      PomElement(
        namespace = "http://maven.apache.org/POM/4.0.0",
        schema = "http://www.w3.org/2001/XMLSchema-instance",
        schemaLocation =
          "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd",
        modelVersion = "4.0.0",
        groupId = "com.google.firebase",
        artifactId = "firebase-bom",
        version = newVersion.toString(),
        packaging = "pom",
        licenses =
          listOf(
            LicenseElement(
              name = "The Apache Software License, Version 2.0",
              url = "http://www.apache.org/licenses/LICENSE-2.0.txt",
              distribution = "repo",
            )
          ),
        dependencyManagement = DependencyManagementElement(artifactsToPublish),
      )

    val bomFile =
      outputDirectory.file(
        "com/google/firebase/firebase-bom/$newVersion/firebase-bom-$newVersion.pom"
      )

    pom.toFile(bomFile.get().asFile.createIfAbsent())
  }

  private fun determineNewBomVersion(
    releasingDependencies: List<ArtifactDependency>
  ): ModuleVersion {
    logger.info("Determining the new bom version")

    val oldBom = gmaven.get().latestPom("com.google.firebase", "firebase-bom")
    val oldBomVersion = ModuleVersion.fromString(oldBom.artifactId, oldBom.version)

    val oldBomDependencies = oldBom.dependencyManagement?.dependencies.orEmpty()
    val changedDependencies = oldBomDependencies.pairBy(releasingDependencies) { it.artifactId }

    val versionBumps =
      changedDependencies.mapNotNull { (old, new) ->
        if (old == null) {
          logger.warn("Dependency was added: ${new?.fullArtifactName}")

          VersionType.MINOR
        } else if (new === null) {
          logger.warn("Dependency was removed: ${old.fullArtifactName}")

          VersionType.MAJOR
        } else {
          old.moduleVersion.bumpFrom(new.moduleVersion)
        }
      }

    val finalBump = versionBumps.minOrNull()
    return oldBomVersion.bump(finalBump)
  }

  /**
   * Validates that the provided bom artifacts satisfy the following constraints:
   * - All are released and live on gmaven.
   * - They include _all_ of the firebase artifacts on gmaven, unless they're specified in
   *   [ignoredArtifacts].+
   *
   * @return The validated artifacts to release.
   * @throws RuntimeException If any of the validations fail.
   */
  private fun validateArtifacts(): List<ArtifactDependency> {
    logger.info("Validating bom artifacts")

    val firebaseArtifacts = bomArtifacts.get().toSet()
    val ignoredArtifacts = ignoredArtifacts.orEmpty().toSet()

    val allFirebaseArtifacts =
      gmaven
        .get()
        .groupIndex("com.google.firebase")
        .map { "${it.groupId}:${it.artifactId}" }
        .toSet()

    val (released, unreleased) =
      firebaseArtifacts
        .associateWith { gmaven.get().groupIndexArtifactOrNull(it) }
        .partitionNotNull()

    if (unreleased.isNotEmpty()) {
      throw RuntimeException(
        """
          |Some artifacts required for bom generation are not live on gmaven yet:
          |${unreleased.joinToString("\n")}
        """
          .trimMargin()
      )
    }

    val requiredArtifacts = allFirebaseArtifacts - ignoredArtifacts
    val missingArtifacts = requiredArtifacts - firebaseArtifacts
    if (missingArtifacts.isNotEmpty()) {
      throw RuntimeException(
        """
          |There are Firebase artifacts missing from the provided bom artifacts.
          |Add the artifacts to the ignoredArtifacts property to ignore them or to the bomArtifacts property to include them in the bom.
          |Dependencies missing:
          |${missingArtifacts.joinToString("\n")}
        """
          .trimMargin()
      )
    }

    return released.values.map { it.toArtifactDependency() }
  }
}
