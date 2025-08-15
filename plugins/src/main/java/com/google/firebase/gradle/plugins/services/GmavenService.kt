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

package com.google.firebase.gradle.plugins.services

import com.google.common.annotations.VisibleForTesting
import com.google.firebase.gradle.plugins.children
import com.google.firebase.gradle.plugins.datamodels.ArtifactDependency
import com.google.firebase.gradle.plugins.datamodels.PomElement
import com.google.firebase.gradle.plugins.multiLine
import com.google.firebase.gradle.plugins.registerIfAbsent
import com.google.firebase.gradle.plugins.textByAttributeOrNull
import java.io.File
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.w3c.dom.Node

/**
 * Gradle build service for facilitating communication with [GMaven](https://maven.google.com).
 *
 * ### Overview
 *
 * Files fetched from GMaven are saved in a local cache on a per-build basis; meaning for any given
 * build, no matter what project or task uses this service, any identical requests will not result
 * in multiple requests to the GMaven servers.
 *
 * This service is also thread-safe; meaning you can safely utilize this service in a concurrent
 * environment (across multiple projects/tasks).
 *
 * ### Bypassing the cache
 *
 * If you need to bypass the cache for any reason, all cached values have a `force-` variant.
 *
 * For example, instead of calling [pomOrNull], you can call [forceGetPom].
 *
 * Calling these methods will fetch the latest files from GMaven, regardless of the state of the
 * cache.
 *
 * *Note: These methods do **not** update the cache either.*
 *
 * ### Usage
 *
 * The build service should ideally be used from within a Task directly.
 *
 * To use the service, you can inject it into your task as needed.
 *
 * ```
 * abstract class MyTask: DefaultTask() {
 *   @get:ServiceReference("gmaven")
 *   abstract val gmaven: Property<GMavenService>
 * }
 * ```
 *
 * #### Plugin usage
 *
 * If you need to access the service from within a plugin, you can do so via the [gmavenService]
 * helper property.
 *
 * ```
 * val latestVersion = project.gmavenService.map {
 *   it.latestVersion("com.google.firebase", "firebase-common")
 * }
 * ```
 */
abstract class GMavenService : BuildService<BuildServiceParameters.None> {
  private val controller = GMavenServiceController()

  /**
   * Gets the latest version of the artifact that has been uploaded to GMaven for a given group.
   *
   * Effectively just a short metadata list of all the published packages and their versions.
   *
   * ```
   * gmaven.groupIndex("com.google.firebase", "com.google.firebase")
   * ```
   *
   * @param groupId The group to search for.
   * @throws RuntimeException If the group index doesn't exist
   * @see forceGetGroupIndex
   * @see GroupIndexArtifact
   */
  fun groupIndex(groupId: String): List<GroupIndexArtifact> = controller.groupIndex(groupId)

  /**
   * Gets the [GroupIndexArtifact] representing a given artifact from gmaven, if any.
   *
   * ```
   * gmaven.groupIndexArtifactOrNull("com.google.firebase", "com.google.firebase")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to search for.
   * @return An [GroupIndexArtifact] representing the artifact, or null if the artifact couldn't be
   *   found.
   * @see groupIndex
   */
  fun groupIndexArtifactOrNull(groupId: String, artifactId: String): GroupIndexArtifact? =
    groupIndex(groupId).find { it.artifactId == artifactId }

  /**
   * Gets the [GroupIndexArtifact] representing a given artifact from gmaven, if any.
   *
   * ```
   * gmaven.groupIndexArtifactOrNull("com.google.firebase:com.google.firebase")
   * ```
   *
   * @param fullArtifactName The artifact to search for, represented as "groupId:artifactId".
   * @return An [GroupIndexArtifact] representing the artifact, or null if the artifact couldn't be
   *   found.
   * @see groupIndex
   */
  fun groupIndexArtifactOrNull(fullArtifactName: String): GroupIndexArtifact? {
    val (groupId, artifactId) = fullArtifactName.split(":")
    return groupIndexArtifactOrNull(groupId, artifactId)
  }

  /**
   * Gets the latest version of the artifact that has been uploaded to GMaven, if any.
   *
   * ```
   * gmaven.latestVersionOrNull("com.google.firebase", "firebase-components") // "18.0.1"
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to search for.
   * @return The latest released version as a string, or null if the artifact couldn't be found.
   * @see latestVersion
   */
  fun latestVersionOrNull(groupId: String, artifactId: String) =
    controller.latestVersionOrNull(groupId, artifactId)

  /**
   * Gets the latest non-alpha version of the artifact that has been uploaded to GMaven, if any.
   *
   * ```
   * gmaven.latestNonAlphaVersionOrNull("com.google.firebase", "firebase-components") // "18.0.1"
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to search for.
   * @return The latest released version as a string, or null if the artifact couldn't be found.
   * @see latestVersion
   */
  fun latestNonAlphaVersionOrNull(groupId: String, artifactId: String) =
    controller.latestNonAlphaVersionOrNull(groupId, artifactId)

  /**
   * Gets the latest non-alpha version of the artifact that has been uploaded to GMaven, if any.
   *
   * ```
   * gmaven.latestNonAlphaVersionOrNull("com.google.firebase", "firebase-components") // "18.0.1"
   * ```
   *
   * @param fullArtifactName The artifact to search for, represented as "groupId:artifactId".
   * @return The latest released version as a string, or null if the artifact couldn't be found.
   * @see latestVersion
   */
  fun latestNonAlphaVersionOrNull(fullArtifactName: String): String? {
    val (groupId, artifactId) = fullArtifactName.split(":")
    return latestNonAlphaVersionOrNull(groupId, artifactId)
  }

  /**
   * Gets the latest version of the artifact that has been uploaded to GMaven, if any.
   *
   * ```
   * gmaven.latestVersionOrNull("com.google.firebase", "firebase-components") // "18.0.1"
   * ```
   *
   * @param fullArtifactName The artifact to search for, represented as "groupId:artifactId".
   * @return The latest released version as a string, or null if the artifact couldn't be found.
   * @see latestVersion
   */
  fun latestVersionOrNull(fullArtifactName: String): String? {
    val (groupId, artifactId) = fullArtifactName.split(":")
    return latestVersionOrNull(groupId, artifactId)
  }

  /**
   * Gets the latest version of the artifact that has been uploaded to GMaven.
   *
   * ```
   * gmaven.latestVersion("com.google.firebase", "firebase-components") // "18.0.1"
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to search for.
   * @return The latest released version as a string.
   * @see latestVersionOrNull
   */
  fun latestVersion(groupId: String, artifactId: String) =
    latestVersionOrNull(groupId, artifactId)
      ?: throw RuntimeException(
        "Failed to get the latest version from gmaven for \'$artifactId\'. Has it been published?"
      )

  /**
   * Checks if an artifact has been published to GMaven.
   *
   * ```
   * gmaven.hasReleasedArtifact("com.google.firebase", "firebase-common") // true
   * gmaven.hasReleasedArtifact("com.google.firebase", "fake-artifact") // false
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to search for.
   * @return True if the artifact has been published to GMaven
   * @see hasReleasedVersion
   */
  fun hasReleasedArtifact(groupId: String, artifactId: String) =
    controller.hasReleasedArtifact(groupId, artifactId)

  /**
   * Checks if a version of an artifact has been published to GMaven.
   *
   * ```
   * gmaven.hasReleasedVersion("com.google.firebase", "firebase-common", "21.0.0") // true
   * gmaven.hasReleasedVersion("com.google.firebase", "firebase-common", "0.0.0") // false
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to search for.
   * @param version The version of the artifact to search for.
   * @return True if the artifact version has been published to GMaven
   * @see hasReleasedArtifact
   */
  fun hasReleasedVersion(groupId: String, artifactId: String, version: String) =
    controller.hasReleasedVersion(groupId, artifactId, version)

  /**
   * Downloads the jar or AAR file for an artifact from GMaven, if any.
   *
   * Will first check for an AAR file, then a jar if it doesn't find an AAR.
   *
   * ```
   * val aarFile = gmaven.artifactOrNull("com.google.firebase", "firebase-common", "21.0.0")
   * val jarFile = gmaven.artifactOrNull("com.google.firebase", "firebase-encoders", "17.0.0")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download.
   * @param version The version of the artifact to download.
   * @return A [File] containing the contents of the AAR or jar, or null if it couldn't be found
   * @see forceGetArtifact
   * @see artifact
   */
  fun artifactOrNull(groupId: String, artifactId: String, version: String) =
    controller.artifactOrNull(groupId, artifactId, version)

  /**
   * Downloads the jar or AAR file for an artifact from GMaven.
   *
   * Will first check for an AAR file, then a jar if it doesn't find an AAR.
   *
   * ```
   * val aarFile = gmaven.artifact("com.google.firebase", "firebase-common", "21.0.0")
   * val jarFile = gmaven.artifact("com.google.firebase", "firebase-encoders", "17.0.0")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download.
   * @param version The version of the artifact to download.
   * @return A [File] containing the contents of the AAR or jar
   * @see artifactOrNull
   * @see latestArtifact
   */
  fun artifact(groupId: String, artifactId: String, version: String) =
    artifactOrNull(groupId, artifactId, version)
      ?: throw RuntimeException(
        "Failed to get the artifact from gmaven for '$artifactId-$version'. Has it been published?"
      )

  /**
   * Downloads the _latest_ jar or AAR file for an artifact from GMaven.
   *
   * Will first check for an AAR file, then a jar if it doesn't find an AAR.
   *
   * ```
   * val aarFile = gmaven.latestArtifact("com.google.firebase", "firebase-common")
   * val jarFile = gmaven.latestArtifact("com.google.firebase", "firebase-encoders")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download.
   * @return A [File] containing the contents of the AAR or jar
   * @see artifact
   */
  fun latestArtifact(groupId: String, artifactId: String) =
    artifact(groupId, artifactId, latestVersion(groupId, artifactId))

  /**
   * Downloads the POM for an artifact from GMaven, if any.
   *
   * ```
   * val pom = gmaven.pomOrNull("com.google.firebase", "firebase-common", "21.0.0")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven, or null if it couldn't be found
   * @see forceGetPom
   * @see pom
   */
  fun pomOrNull(groupId: String, artifactId: String, version: String) =
    controller.pomOrNull(groupId, artifactId, version)

  /**
   * Downloads the POM for an artifact from GMaven.
   *
   * ```
   * val pom = gmaven.pom("com.google.firebase", "firebase-common", "21.0.0")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven
   * @see pomOrNull
   * @see latestPom
   */
  fun pom(groupId: String, artifactId: String, version: String) =
    pomOrNull(groupId, artifactId, version)
      ?: throw RuntimeException(
        "Failed to get the pom from gmaven for '$artifactId-$version'. Has it been published?"
      )

  /**
   * Downloads the _latest_ POM for an artifact from GMaven.
   *
   * ```
   * val pom = gmaven.latestPom("com.google.firebase", "firebase-common")
   * ```
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download the pom for.
   * @return A [PomElement] matching the pom on GMaven
   * @see pom
   */
  fun latestPom(groupId: String, artifactId: String) =
    pom(groupId, artifactId, latestVersion(groupId, artifactId))

  /**
   * Downloads the jar or AAR file for an artifact from GMaven, if any.
   *
   * In contrast to [artifactOrNull], this method doesn't check the cache, nor does it cache the
   * return value.
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven, or null if it couldn't be found
   * @see artifactOrNull
   */
  fun forceGetArtifact(groupId: String, artifactId: String, version: String) =
    controller.forceGetArtifact(groupId, artifactId, version)

  /**
   * Downloads the POM for an artifact from GMaven, if any.
   *
   * In contrast to [pomOrNull], this method doesn't check the cache, nor does it cache the return
   * value.
   *
   * @param groupId The group to search under.
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven, or null if it couldn't be found
   * @see pomOrNull
   */
  fun forceGetPom(groupId: String, artifactId: String, version: String) =
    controller.forceGetPom(groupId, artifactId, version)

  /**
   * Downloads the `group-index.xml` file from GMaven.
   *
   * This does _not_ update the cached [groupIndex].
   *
   * @param groupId The group to search under.
   * @return A list of [GroupIndexArtifact] parsed from the `group-index.xml`
   * @see groupIndex
   */
  fun forceGetGroupIndex(groupId: String) = controller.forceGetGroupIndex(groupId)
}

/**
 * Helper property for getting the [GMavenService] instance from a plugin instance.
 *
 * Will register the service if it isn't found.
 */
val Project.gmavenService: Provider<GMavenService>
  get() = gradle.sharedServices.registerIfAbsent<GMavenService, _>("gmaven")

/**
 * Controller for facilitating communication with GMaven, and caching the result.
 *
 * Ideally, you should be using [GMavenService] instead of this. This primarily exists so that we
 * can test the service outside the scope of Gradle.
 *
 * @see GMavenService
 */
@VisibleForTesting
class GMavenServiceController(
  private val downloadDirectory: Path = createTempDirectory(),
  private val gmaven: DocumentService = DocumentService(),
  private val logger: Logger = Logging.getLogger(GMavenServiceController::class.java),
) {
  private val groupIndexCache = ConcurrentHashMap<String, List<GroupIndexArtifact>>()
  private val artifactCache = ConcurrentHashMap<String, File?>()
  private val pomFileCache = ConcurrentHashMap<String, PomElement?>()

  /** @see GMavenService.latestVersionOrNull */
  fun latestVersionOrNull(groupId: String, artifactId: String): String? {
    return findFirebaseArtifact(groupId, artifactId)?.latestVersion
  }

  /** @see GMavenService.latestNonAlphaVersionOrNull */
  fun latestNonAlphaVersionOrNull(groupId: String, artifactId: String): String? {
    return findFirebaseArtifact(groupId, artifactId)?.latestNonAlphaVersion
  }

  /** @see GMavenService.hasReleasedArtifact */
  fun hasReleasedArtifact(groupId: String, artifactId: String): Boolean {
    return findFirebaseArtifact(groupId, artifactId) !== null
  }

  /** @see GMavenService.hasReleasedVersion */
  fun hasReleasedVersion(groupId: String, artifactId: String, version: String): Boolean {
    return findFirebaseArtifact(groupId, artifactId)?.versions?.contains(version) ?: false
  }

  /** @see GMavenService.artifactOrNull */
  fun artifactOrNull(groupId: String, artifactId: String, version: String): File? {
    val name = artifactName(artifactId, version)
    val cacheName = artifactCacheName(groupId, name)
    logger.debug("Getting artifact '$cacheName'")

    return artifactCache.computeIfAbsent(cacheName) {
      logger.info("Artifact missing from cache '$cacheName'")

      if (hasReleasedVersion(groupId, artifactId, version)) {
        forceGetArtifact(groupId, artifactId, version)
      } else {
        null
      }
    }
  }

  /** @see GMavenService.pomOrNull */
  fun pomOrNull(groupId: String, artifactId: String, version: String): PomElement? {
    val name = artifactName(artifactId, version)
    val cacheName = artifactCacheName(groupId, name)
    logger.debug("Getting pom '$cacheName'")

    return pomFileCache.computeIfAbsent(cacheName) {
      logger.info("Pom missing from cache: '$cacheName'")

      if (hasReleasedVersion(groupId, artifactId, version)) {
        forceGetPom(groupId, artifactId, version)
      } else {
        null
      }
    }
  }

  /** @see GMavenService.groupIndex */
  fun groupIndex(groupId: String): List<GroupIndexArtifact> {
    logger.debug("Getting group index '$groupId'")

    return groupIndexCache.computeIfAbsent(groupId) {
      logger.info("Group index missing from cache: '$groupId'")

      forceGetGroupIndex(groupId)
    }
  }

  /** @see GMavenService.forceGetArtifact */
  fun forceGetArtifact(groupId: String, artifactId: String, version: String): File? {
    logger.debug("Fetching artifact '$groupId:$artifactId-$version' from gmaven")

    return forceGetAAR(groupId, artifactId, version) ?: forceGetJar(groupId, artifactId, version)
  }

  /** @see GMavenService.forceGetPom */
  fun forceGetPom(groupId: String, artifactId: String, version: String): PomElement? {
    val name = artifactName(artifactId, version)
    val cacheName = artifactCacheName(groupId, name)
    logger.info("Fetching pom '$cacheName' from gmaven")

    val artifactPath = "${rootForGroupId(groupId)}/$artifactId/$version/$name.pom"
    val document = gmaven.downloadDocument(artifactPath)

    if (document === null) {
      logger.info("Pom not present in gmaven: '$cacheName'")
    }

    return document?.let { PomElement.fromElement(it.documentElement) }
  }

  /** @see GMavenService.forceGetGroupIndex */
  fun forceGetGroupIndex(groupId: String): List<GroupIndexArtifact> {
    logger.info("Fetching group index from gmaven for group: $groupId")

    val document =
      gmaven.downloadDocument("${rootForGroupId(groupId)}/group-index.xml")
        ?: throw RuntimeException("Failed to find the group index file. Is GMaven offline?")

    val groups =
      document.childNodes
        .children()
        .flatMap { group ->
          group.childNodes.children().map { GroupIndexArtifact.fromNode(group.nodeName, it) }
        }
        .groupBy { it.groupId }

    return groups[groupId] ?: emptyList()
  }

  private fun forceGetJar(groupId: String, artifactId: String, version: String): File? {
    val name = artifactName(artifactId, version)
    val cacheName = artifactCacheName(groupId, name)
    logger.debug("Fetching jar '$cacheName' from gmaven")

    return gmaven
      .downloadToFile(
        "${rootForGroupId(groupId)}/$artifactId/$version/$name.jar",
        createTempFile(downloadDirectory, name, ".jar").toFile(),
      )
      .also { it ?: logger.info("jar not present in gmaven '$cacheName'") }
  }

  private fun forceGetAAR(groupId: String, artifactId: String, version: String): File? {
    val name = artifactName(artifactId, version)
    val cacheName = artifactCacheName(groupId, name)
    logger.debug("Fetching AAR '$cacheName' from gmaven")

    return gmaven
      .downloadToFile(
        "${rootForGroupId(groupId)}/$artifactId/$version/$name.aar",
        createTempFile(downloadDirectory, name, ".aar").toFile(),
      )
      .also { it ?: logger.info("AAR not present in gmaven '$cacheName'") }
  }

  /** Searches the cached group index for the given artifact. */
  private fun findFirebaseArtifact(groupId: String, artifactId: String): GroupIndexArtifact? {
    return groupIndex(groupId)
      .find { it.artifactId == artifactId }
      .also { it ?: logger.info("Artifact not found in the group index: '$groupId:$artifactId'") }
  }

  private fun artifactName(artifactId: String, version: String) = "$artifactId-$version"

  private fun artifactCacheName(groupId: String, artifactName: String) = "$groupId:$artifactName"

  private fun rootForGroupId(groupId: String) =
    "$GMAVEN_ROOT/${groupId.split(".").joinToString("/")}"

  companion object {
    private const val GMAVEN_ROOT = "https://dl.google.com/dl/android/maven2/"
  }
}

/**
 * Representation of an artifact entry in a `group-index.xml` file.
 *
 * @see GMavenService.groupIndex
 */
data class GroupIndexArtifact(
  val groupId: String,
  val artifactId: String,
  val versions: List<String>,
  val latestVersion: String = versions.last(),
  val latestNonAlphaVersion: String? = versions.findLast { !it.contains("alpha") },
) {

  /**
   * Converts this artifact into a [ArtifactDependency], using the [latestVersion] as the
   * corresponding version.
   */
  fun toArtifactDependency() =
    ArtifactDependency(groupId = groupId, artifactId = artifactId, version = latestVersion)

  /**
   * Returns this artifact as a fully qualified dependency string.
   *
   * ```
   * "com.google.firebase:firebase-firestore:1.0.0"
   * ```
   */
  override fun toString(): String {
    return "$groupId:$artifactId:$latestVersion"
  }

  companion object {
    /**
     * Create a [GroupIndexArtifact] from an html [Node].
     *
     * @param groupId The group that this artifact belongs to.
     * @param node The HTML node that contains the data for this artifact.
     * @return An instance of [GroupIndexArtifact] representing the provided [node].
     * @throws RuntimeException If the node couldn't be parsed for whatever reason.
     */
    fun fromNode(groupId: String, node: Node): GroupIndexArtifact {
      val versions =
        node.textByAttributeOrNull("versions")?.split(",")
          ?: throw RuntimeException(
            "GroupIndex node is missing a versions attribute: ${node.nodeName}"
          )

      if (versions.isEmpty())
        throw RuntimeException(
          multiLine(
            "GroupIndex node has a versions attribute without any content: ${node.nodeName}",
            "This shouldn't happen. If this is happening, and is expected behavior, then this check should be removed.",
          )
        )

      return GroupIndexArtifact(groupId, node.nodeName, versions)
    }
  }
}
