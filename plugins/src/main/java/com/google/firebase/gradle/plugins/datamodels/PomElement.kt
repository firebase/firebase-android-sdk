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

package com.google.firebase.gradle.plugins.datamodels

import com.google.firebase.gradle.plugins.ModuleVersion
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.newReader
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlChildrenName
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.xmlStreaming
import org.w3c.dom.Element

/**
 * Representation of a `<license />` element in a a pom file.
 *
 * @see PomElement
 */
@Serializable
@XmlSerialName("license")
data class LicenseElement(
  @XmlElement val name: String,
  @XmlElement val url: String? = null,
  @XmlElement val distribution: String? = null,
) : java.io.Serializable

/**
 * Representation of an `<scm />` element in a a pom file.
 *
 * @see PomElement
 */
@Serializable
@XmlSerialName("scm")
data class SourceControlManagement(
  @XmlElement val connection: String,
  @XmlElement val url: String,
) : java.io.Serializable

/**
 * Representation of a `<dependency />` element in a pom file.
 *
 * @see PomElement
 */
@Serializable
@XmlSerialName("dependency")
data class ArtifactDependency(
  @XmlElement val groupId: String,
  @XmlElement val artifactId: String,
  // Can be null if the artifact derives its version from a bom
  @XmlElement val version: String? = null,
  @XmlElement val type: String? = null,
  @XmlElement val scope: String? = null,
) : java.io.Serializable {
  /**
   * Returns the artifact dependency as a a gradle dependency string.
   *
   * ```
   * implementation("com.google.firebase:firebase-firestore:1.0.0")
   * ```
   *
   * @see configuration
   * @see simpleDepString
   */
  override fun toString() = "$configuration(\"$simpleDepString\")"
}

/**
 * The artifact type of this dependency, or the default inferred by gradle.
 *
 * We use a separate variable instead of inferring the default in the constructor so we can
 * serialize instances of [ArtifactDependency] that should specifically _not_ have a type in the
 * output (like in [DependencyManagementElement] instances).
 */
val ArtifactDependency.typeOrDefault: String
  get() = type ?: "jar"

/**
 * The artifact scope of this dependency, or the default inferred by gradle.
 *
 * We use a separate variable instead of inferring the default in the constructor so we can
 * serialize instances of [ArtifactDependency] that should specifically _not_ have a scope in the
 * output (like in [DependencyManagementElement] instances).
 */
val ArtifactDependency.scopeOrDefault: String
  get() = scope ?: "compile"

/**
 * The [version][ArtifactDependency.version] represented as a [ModuleVersion].
 *
 * @throws RuntimeException if the version isn't valid semver, or it's missing.
 */
val ArtifactDependency.moduleVersion: ModuleVersion
  get() =
    version?.let { ModuleVersion.fromString(artifactId, it) }
      ?: throw RuntimeException(
        "Missing required version property for artifact dependency: $artifactId"
      )

/**
 * The fully qualified name of the artifact.
 *
 * Shorthand for:
 * ```
 * "${artifact.groupId}:${artifact.artifactId}"
 * ```
 */
val ArtifactDependency.fullArtifactName: String
  get() = "$groupId:$artifactId"

/**
 * A string representing the dependency as a maven artifact marker.
 *
 * ```
 * "com.google.firebase:firebase-common:21.0.0"
 * ```
 */
val ArtifactDependency.simpleDepString: String
  get() = "$fullArtifactName${version?.let { ":$it" } ?: ""}"

/** The gradle configuration that this dependency would apply to (eg; `api` or `implementation`). */
val ArtifactDependency.configuration: String
  get() = if (scopeOrDefault == "compile") "api" else "implementation"

@Serializable
@XmlSerialName("dependencyManagement")
data class DependencyManagementElement(
  @XmlChildrenName("dependency") val dependencies: List<ArtifactDependency>? = null
) : java.io.Serializable

/** Representation of a `<project />` element within a `pom.xml` file. */
@Serializable
@XmlSerialName("project")
data class PomElement(
  @XmlSerialName("xmlns") val namespace: String? = null,
  @XmlSerialName("xmlns:xsi") val schema: String? = null,
  @XmlSerialName("xsi:schemaLocation") val schemaLocation: String? = null,
  @XmlElement val modelVersion: String,
  @XmlElement val groupId: String,
  @XmlElement val artifactId: String,
  @XmlElement val version: String,
  @XmlElement val packaging: String? = null,
  @XmlChildrenName("license") val licenses: List<LicenseElement>? = null,
  @XmlElement val scm: SourceControlManagement? = null,
  @XmlElement val dependencyManagement: DependencyManagementElement? = null,
  @XmlChildrenName("dependency") val dependencies: List<ArtifactDependency>? = null,
) : java.io.Serializable {
  /**
   * Serializes this pom element into a valid XML element and saves it to the specified [file].
   *
   * @param file Where to save the serialized pom to
   * @return The provided file, for chaining purposes.
   * @see fromFile
   */
  fun toFile(file: File): File {
    file.writeText(toString())
    return file
  }

  /**
   * Serializes this pom element into a valid XML element.
   *
   * @see toFile
   */
  override fun toString(): String {
    return xml.encodeToString(this)
  }

  companion object {
    private val xml = XML {
      indent = 2
      xmlDeclMode = XmlDeclMode.None
    }

    /**
     * Deserializes a [PomElement] from a `pom.xml` file.
     *
     * @param file The file that contains the pom element.
     * @return The deserialized [PomElement]
     * @see toFile
     * @see fromElement
     */
    fun fromFile(file: File): PomElement =
      fromElement(
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
      )

    /**
     * Deserializes a [PomElement] from a document [Element].
     *
     * @param element The HTML element representing the pom element.
     * @return The deserialized [PomElement]
     * @see fromFile
     */
    fun fromElement(element: Element): PomElement =
      xml.decodeFromReader(xmlStreaming.newReader(element))
  }
}
