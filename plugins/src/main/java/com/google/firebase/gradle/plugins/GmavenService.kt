package com.google.firebase.gradle.plugins

import com.google.common.annotations.VisibleForTesting
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.net.URL
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.kotlin.dsl.registerIfAbsent
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

// TODO: organize data models into some other space.

/**
 * Representation of the root metadata in a pom file.
 *
 * @see PomElement
 */
data class XMLMetadata(val namespace: String?, val schema: String?, val schemaLocation: String?) {
  companion object {
    fun fromElement(element: Element): XMLMetadata =
      with(element) {
        XMLMetadata(
          textByAttributeOrNull("xmlns"),
          textByAttributeOrNull("xmlns:xsi"),
          textByAttributeOrNull("xsi:schemaLocation"),
        )
      }
  }
}

/**
 * Representation of a `<license />` element in a a pom file.
 *
 * @see PomElement
 */
data class LicenseElement(val name: String, val url: String? = null) {
  companion object {
    fun fromElement(element: Element): LicenseElement =
      with(element) { LicenseElement(textByTag("name"), textByTagOrNull("url")) }
  }
}

/**
 * Representation of an `<scm />` element in a a pom file.
 *
 * @see PomElement
 */
data class SourceControlManagement(val connection: String, val url: String) {
  companion object {
    fun fromElement(element: Element): SourceControlManagement =
      with(element) { SourceControlManagement(textByTag("connection"), textByTag("url")) }
  }
}

/**
 * Representation of a `<dependency />` element in a pom file.
 *
 * @see PomElement
 */
data class ArtifactDependency(
  val groupId: String,
  val artifactId: String,
  // Can be null if the artifact derives its version from a bom
  val version: String? = null,
  val type: String = "jar",
  val scope: String = "compile",
) {

  companion object {
    fun fromElement(element: Element): ArtifactDependency =
      with(element) {
        ArtifactDependency(
          textByTag("groupId"),
          textByTag("artifactId"),
          textByTagOrNull("version"),
          textByTagOrNull("type") ?: "jar",
          textByTagOrNull("scope") ?: "compile",
        )
      }
  }

  /**
   * A string representing the dependency as a maven artifact marker.
   *
   * ```
   * "com.google.firebase:firebase-common:21.0.0"
   * ```
   */
  val simpleDepString = "$groupId:$artifactId${version?.let { ":$it" } ?: ""}"

  /**
   * The gradle configuration that this dependency would apply to (eg; `api` or `implementation`).
   */
  val configuration = if (scope == "compile") "api" else "implementation"

  override fun toString() = "$configuration(\"$simpleDepString\")"
}

/** Representation of a `pom.xml`. */
data class PomElement(
  val metadata: XMLMetadata,
  val modelVersion: String,
  val groupId: String,
  val artifactId: String,
  val version: String,
  val packaging: String?,
  val licenses: List<LicenseElement>,
  val scm: SourceControlManagement? = null,
  val dependencies: List<ArtifactDependency>,
) {
  companion object {
    fun fromFile(file: File): PomElement =
      fromElement(
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
      )

    fun fromElement(element: Element): PomElement =
      with(element) {
        val scm =
          element.findElementsByTag("scm").firstOrNull()?.let {
            SourceControlManagement.fromElement(it)
          }

        val licenses = element.findElementsByTag("license").map { LicenseElement.fromElement(it) }

        val dependencies =
          element.findElementsByTag("dependency").map { ArtifactDependency.fromElement(it) }

        PomElement(
          XMLMetadata.fromElement(element),
          textByTag("modelVersion"),
          textByTag("groupId"),
          textByTag("artifactId"),
          textByTag("version"),
          textByTagOrNull("packaging"),
          licenses.toList(),
          scm,
          dependencies.toList(),
        )
      }
  }
}

/**
 * Representation of an artifact entry in a `group-index.xml` file.
 *
 * @see GroupIndex
 */
data class GroupIndexArtifact(
  val groupId: String,
  val artifactId: String,
  val versions: List<String>,
  val latestVersion: String,
) {
  companion object {
    fun fromNode(groupId: String, node: Node): GroupIndexArtifact {
      val versions =
        node.textByAttributeOrNull("versions")?.split(",")
          ?: throw RuntimeException(
            "GroupIndex node is missing a versions attribute: ${node.nodeName}"
          )

      // This shouldn't happen... if it does (for some reason), then we can remove this check in the
      // future.
      if (versions.isEmpty())
        throw RuntimeException(
          "GroupIndex node has a versions attribute without any content: ${node.nodeName}"
        )

      return GroupIndexArtifact(groupId, node.nodeName, versions, versions.last())
    }
  }

  override fun toString(): String {
    return "$groupId:$artifactId:$latestVersion"
  }
}

/**
 * Representation of a `group-index.xml` file.
 *
 * @see findArtifact
 */
data class GroupIndex(val groups: Map<String, List<GroupIndexArtifact>>) {
  /**
   * Searches for an artifact in the group index.
   *
   * @return A [GroupIndexArtifact] representing the artifact, or null if it wasn't found.
   */
  fun findArtifact(groupId: String, artifactId: String): GroupIndexArtifact? {
    return groups[groupId]?.find { it.artifactId == artifactId }
  }
}

/**
 * Wrapper around [Documents][Document].
 *
 * Abstracts some common download functionality when dealing with documents, and also allows the
 * behavior to be more easily mocked and tested.
 */
class DocumentService {
  /**
   * Opens an [InputStream] at the specified [url].
   *
   * It's the caller's responsibility to _close_ the stream when done.
   */
  fun openStream(url: String): InputStream = URL(url).openStream()

  /**
   * Downloads the [Document] from the specified [url], and saves it to a [file].
   *
   * @return The same [file] instance when the document is downloaded, or null if the document
   *   wasn't found.
   */
  fun downloadToFile(url: String, file: File): File? =
    try {
      openStream(url).use { file.writeStream(it) }
    } catch (e: FileNotFoundException) {
      null
    }

  /**
   * Downloads the [Document] from the specified [url].
   *
   * @return The downloaded [Document] instance, or null if the document wasn't found.
   */
  fun downloadDocument(url: String): Document? =
    try {
      DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(openStream(url))
    } catch (e: FileNotFoundException) {
      null
    }
}

/**
 * Controller for facilitating communication with GMaven, and caching the result.
 *
 * Ideally, you should be using [GMavenServiceGradle] instead of this. This primarily exists so that
 * we can test the service outside the scope of Gradle.
 *
 * @see GMavenServiceGradle
 */
@VisibleForTesting
class GMavenServiceController(
  private val downloadDirectory: Path = createTempDirectory(),
  private val gmaven: DocumentService = DocumentService(),
  private val logger: Logger = Logging.getLogger(GMavenServiceController::class.java),
) {
  /** @see GMavenServiceGradle.groupIndex */
  val groupIndex = forceGetGroupIndex()

  private val artifactCache = ConcurrentHashMap<String, File?>()
  private val pomFileCache = ConcurrentHashMap<String, PomElement?>()

  /** @see GMavenServiceGradle.latestVersionOrNull */
  fun latestVersionOrNull(artifactId: String): String? {
    return findFirebaseArtifact(artifactId)?.latestVersion
  }

  /** @see GMavenServiceGradle.hasReleasedArtifact */
  fun hasReleasedArtifact(artifactId: String): Boolean {
    return findFirebaseArtifact(artifactId) !== null
  }

  /** @see GMavenServiceGradle.hasReleasedVersion */
  fun hasReleasedVersion(artifactId: String, version: String): Boolean {
    return findFirebaseArtifact(artifactId)?.versions?.contains(version) ?: false
  }

  /** @see GMavenServiceGradle.artifactOrNull */
  fun artifactOrNull(artifactId: String, version: String): File? {
    val name = artifactName(artifactId, version)
    logger.debug("Getting artifact '$name'")

    return artifactCache.computeIfAbsent(name) {
      logger.info("Artifact missing from cache '$name'")

      if (hasReleasedVersion(artifactId, version)) {
        forceGetArtifact(artifactId, version)
      } else {
        null
      }
    }
  }

  /** @see GMavenServiceGradle.pomOrNull */
  fun pomOrNull(artifactId: String, version: String): PomElement? {
    val name = artifactName(artifactId, version)
    logger.debug("Getting pom '$name'")

    return pomFileCache.computeIfAbsent(name) {
      logger.info("Pom missing from cache: '$name'")

      if (hasReleasedVersion(artifactId, version)) {
        forceGetPom(artifactId, version)
      } else {
        null
      }
    }
  }

  /** @see GMavenServiceGradle.forceGetArtifact */
  fun forceGetArtifact(artifactId: String, version: String): File? {
    logger.debug("Fetching artifact '${artifactName(artifactId, version)}' from gmaven")

    return forceGetAAR(artifactId, version) ?: forceGetJar(artifactId, version)
  }

  /** @see GMavenServiceGradle.forceGetPom */
  fun forceGetPom(artifactId: String, version: String): PomElement? {
    val name = artifactName(artifactId, version)
    logger.info("Fetching pom '$name' from gmaven")

    val artifactPath = "$GMAVEN_ROOT/$artifactId/$version/$name.pom"
    val document = gmaven.downloadDocument(artifactPath)

    if (document === null) {
      logger.info("Pom not present in gmaven: '$name'")
    }

    return document?.let { PomElement.fromElement(it.documentElement) }
  }

  /** @see GMavenServiceGradle.forceGetGroupIndex */
  fun forceGetGroupIndex(): GroupIndex {
    logger.info("Fetching group index from gmaven")

    val document =
      gmaven.downloadDocument("$GMAVEN_ROOT/group-index.xml")
        ?: throw RuntimeException("Failed to find the group index file. Is GMaven offline?")

    val groups =
      document.childNodes
        .children()
        .flatMap { group ->
          group.childNodes.children().map { GroupIndexArtifact.fromNode(group.nodeName, it) }
        }
        .groupBy { it.groupId }

    return GroupIndex(groups)
  }

  private fun forceGetJar(artifactId: String, version: String): File? {
    val name = artifactName(artifactId, version)
    logger.debug("Fetching jar '$name' from gmaven")

    return gmaven
      .downloadToFile(
        "$GMAVEN_ROOT/$artifactId/$version/$name.jar",
        createTempFile(downloadDirectory, name, ".jar").toFile(),
      )
      .also { it ?: logger.info("jar not present in gmaven '$name'") }
  }

  private fun forceGetAAR(artifactId: String, version: String): File? {
    val name = artifactName(artifactId, version)
    logger.debug("Fetching AAR '$name' from gmaven")

    return gmaven
      .downloadToFile(
        "$GMAVEN_ROOT/$artifactId/$version/$name.aar",
        createTempFile(downloadDirectory, name, ".aar").toFile(),
      )
      .also { it ?: logger.info("AAR not present in gmaven '$name'") }
  }

  /** Searches the cached group index for the given artifact. */
  private fun findFirebaseArtifact(artifactId: String): GroupIndexArtifact? {
    return groupIndex.findArtifact("com.google.firebase", artifactId).also {
      it ?: logger.info("Artifact not found in the group index: '$artifactId'")
    }
  }

  private fun artifactName(artifactId: String, version: String) = "$artifactId-$version"

  companion object {
    private const val GMAVEN_ROOT = "https://dl.google.com/dl/android/maven2/com/google/firebase"
  }
}

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
 *   abstract val gmaven: Property<GMavenServiceGradle>
 * }
 * ```
 *
 * #### Plugin usage
 *
 * If you need to access the service from within a plugin, you can do so via the [gmavenService]
 * helper property.
 *
 * ```
 * val latestVersion = project.gmavenService.map { it.latestVersion("firebase-common") }
 * ```
 */
abstract class GMavenServiceGradle : BuildService<BuildServiceParameters.None> {
  private val controller = GMavenServiceController()

  /**
   * The [GroupIndex] for `com.google.firebase`.
   *
   * Short metadata list of all the published packages and their versions.
   *
   * @see forceGetGroupIndex
   */
  val groupIndex: GroupIndex = controller.groupIndex

  /**
   * Gets the latest version of the artifact that has been uploaded to GMaven, if any.
   *
   * ```
   * gmaven.latestVersionOrNull("firebase-components") // "18.0.1"
   * ```
   *
   * @param artifactId The artifact to search for.
   * @return The latest released version as a string, or null if the artifact couldn't be found.
   * @see latestVersion
   */
  fun latestVersionOrNull(artifactId: String) = controller.latestVersionOrNull(artifactId)

  /**
   * Gets the latest version of the artifact that has been uploaded to GMaven.
   *
   * ```
   * gmaven.latestVersion("firebase-components") // "18.0.1"
   * ```
   *
   * @param artifactId The artifact to search for.
   * @return The latest released version as a string.
   * @see latestVersionOrNull
   */
  fun latestVersion(artifactId: String) =
    latestVersionOrNull(artifactId)
      ?: throw RuntimeException(
        "Failed to get the latest version from gmaven for \'$artifactId\'. Has it been published?"
      )

  /**
   * Checks if an artifact has been published to GMaven.
   *
   * ```
   * gmaven.hasReleasedArtifact("firebase-common") // true
   * gmaven.hasReleasedArtifact("fake-artifact") // false
   * ```
   *
   * @param artifactId The artifact to search for.
   * @return True if the artifact has been published to GMaven
   * @see hasReleasedVersion
   */
  fun hasReleasedArtifact(artifactId: String) = controller.hasReleasedArtifact(artifactId)

  /**
   * Checks if a version of an artifact has been published to GMaven.
   *
   * ```
   * gmaven.hasReleasedVersion("firebase-common", "21.0.0") // true
   * gmaven.hasReleasedVersion("firebase-common", "0.0.0") // false
   * ```
   *
   * @param artifactId The artifact to search for.
   * @param version The version of the artifact to search for.
   * @return True if the artifact version has been published to GMaven
   * @see hasReleasedArtifact
   */
  fun hasReleasedVersion(artifactId: String, version: String) =
    controller.hasReleasedVersion(artifactId, version)

  /**
   * Downloads the jar or AAR file for an artifact from GMaven, if any.
   *
   * Will first check for an AAR file, then a jar if it doesn't find an AAR.
   *
   * ```
   * val aarFile = gmaven.artifactOrNull("firebase-common", "21.0.0")
   * val jarFile = gmaven.artifactOrNull("firebase-encoders", "17.0.0")
   * ```
   *
   * @param artifactId The artifact to download.
   * @param version The version of the artifact to download.
   * @return A [File] containing the contents of the AAR or jar, or null if it couldn't be found
   * @see forceGetArtifact
   * @see artifact
   */
  fun artifactOrNull(artifactId: String, version: String) =
    controller.artifactOrNull(artifactId, version)

  /**
   * Downloads the jar or AAR file for an artifact from GMaven.
   *
   * Will first check for an AAR file, then a jar if it doesn't find an AAR.
   *
   * ```
   * val aarFile = gmaven.artifact("firebase-common", "21.0.0")
   * val jarFile = gmaven.artifact("firebase-encoders", "17.0.0")
   * ```
   *
   * @param artifactId The artifact to download.
   * @param version The version of the artifact to download.
   * @return A [File] containing the contents of the AAR or jar
   * @see artifactOrNull
   * @see latestArtifact
   */
  fun artifact(artifactId: String, version: String) =
    artifactOrNull(artifactId, version)
      ?: throw RuntimeException(
        "Failed to get the artifact from gmaven for '$artifactId-$version'. Has it been published?"
      )

  /**
   * Downloads the _latest_ jar or AAR file for an artifact from GMaven.
   *
   * Will first check for an AAR file, then a jar if it doesn't find an AAR.
   *
   * ```
   * val aarFile = gmaven.latestArtifact("firebase-common")
   * val jarFile = gmaven.latestArtifact("firebase-encoders")
   * ```
   *
   * @param artifactId The artifact to download.
   * @return A [File] containing the contents of the AAR or jar
   * @see artifact
   */
  fun latestArtifact(artifactId: String) = artifact(artifactId, latestVersion(artifactId))

  /**
   * Downloads the POM for an artifact from GMaven, if any.
   *
   * ```
   * val pom = gmaven.pomOrNull("firebase-common", "21.0.0")
   * ```
   *
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven, or null if it couldn't be found
   * @see forceGetPom
   * @see pom
   */
  fun pomOrNull(artifactId: String, version: String) = controller.pomOrNull(artifactId, version)

  /**
   * Downloads the POM for an artifact from GMaven.
   *
   * ```
   * val pom = gmaven.pom("firebase-common", "21.0.0")
   * ```
   *
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven
   * @see pomOrNull
   * @see latestPom
   */
  fun pom(artifactId: String, version: String) =
    pomOrNull(artifactId, version)
      ?: throw RuntimeException(
        "Failed to get the pom from gmaven for '$artifactId-$version'. Has it been published?"
      )

  /**
   * Downloads the _latest_ POM for an artifact from GMaven.
   *
   * ```
   * val pom = gmaven.latestPom("firebase-common")
   * ```
   *
   * @param artifactId The artifact to download the pom for.
   * @return A [PomElement] matching the pom on GMaven
   * @see pom
   */
  fun latestPom(artifactId: String) = pom(artifactId, latestVersion(artifactId))

  /**
   * Downloads the jar or AAR file for an artifact from GMaven, if any.
   *
   * In contrast to [artifactOrNull], this method doesn't check the cache, nor does it cache the
   * return value.
   *
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven, or null if it couldn't be found
   * @see artifactOrNull
   */
  fun forceGetArtifact(artifactId: String, version: String) =
    controller.forceGetArtifact(artifactId, version)

  /**
   * Downloads the POM for an artifact from GMaven, if any.
   *
   * In contrast to [pomOrNull], this method doesn't check the cache, nor does it cache the return
   * value.
   *
   * @param artifactId The artifact to download the pom for.
   * @param version The version of the pom to download.
   * @return A [PomElement] matching the pom on GMaven, or null if it couldn't be found
   * @see pomOrNull
   */
  fun forceGetPom(artifactId: String, version: String) = controller.forceGetPom(artifactId, version)

  /**
   * Downloads the `group-index.xml` file from GMaven.
   *
   * This does _not_ update the cached [groupIndex].
   *
   * @return A [GroupIndex] parsed from the `group-index.xml`
   * @see groupIndex
   */
  fun forceGetGroupIndex() = controller.forceGetGroupIndex()
}

/**
 * Helper property for getting the [GMavenServiceGradle] instance from a plugin instance.
 *
 * Will register the service if it isn't found.
 */
val Project.gmavenService: Provider<GMavenServiceGradle>
  get() = gradle.sharedServices.registerIfAbsent<GMavenServiceGradle, _>("gmaven")
