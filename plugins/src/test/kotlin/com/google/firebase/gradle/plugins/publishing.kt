/*
 * Copyright 2020 Google LLC
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

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * A dynamically created gradle project to use in a test.
 *
 * @see FirebaseTestController
 * @see generateBuildFile
 */
open class TestProject(
  val name: String,
  val group: String = "com.example",
  val version: String? = null,
  val latestReleasedVersion: String? = null,
  val projectDependencies: Set<TestProject> = setOf(),
  val externalDependencies: Set<ArtifactDependency> = setOf(),
  val extraDependencies: String = "",
  val plugins: String = "",
  val extra: String = "",
  val android: String = "",
  val libraryType: LibraryType = if (android.isEmpty()) LibraryType.JAVA else LibraryType.ANDROID,
) {
  val path = ":$name"

  /** Generates the `build.gradle.kts` that would represent this project. */
  open fun generateBuildFile(): String {
    return """
      ${generatePluginBlock()}
      group = "$group"
      ${version?.let { "version = \"$it\"" } ?: ""}
      ${latestReleasedVersion?.let { "ext[\"latestReleasedVersion\"] = \"$it\"" } ?: ""}
      ${generateAndroidBlock()}
      ${generateDependenciesBlock()}
      $extra
    """
      .trimIndent()
  }

  open fun generateAndroidBlock(): String {
    if (libraryType === LibraryType.JAVA) return ""
    return """
      android {
        compileSdk = 30
        namespace = "$group"
        $android
      }
    """
      .trimIndent()
  }

  open fun generateDependenciesBlock(): String {
    if (
      projectDependencies.isEmpty() && externalDependencies.isEmpty() && extraDependencies.isBlank()
    )
      return ""
    return """
      dependencies {
        ${projectDependencies.joinToString("\n") { "implementation(${it.toDependency(true)})" }}
        ${externalDependencies.joinToString("\n")}
        $extraDependencies
      }
    """
      .trimIndent()
  }

  open fun generatePluginBlock(): String {
    if (plugins.isBlank()) return ""
    return """
      plugins {
       $plugins
      }
    """
      .trimIndent()
  }
}

/** A [TestProject] that specifically represents a firebase library. */
class FirebaseTestProject(
  name: String,
  group: String = "com.example",
  version: String = "undefined",
  val expectedVersion: String = version,
  latestReleasedVersion: String? = null,
  projectDependencies: Set<TestProject> = setOf(),
  externalDependencies: Set<ArtifactDependency> = setOf(),
  val libraryGroup: String? = null,
  val customizePom: String? = null,
  val publishJavadoc: Boolean = false,
  libraryType: LibraryType = LibraryType.ANDROID,
) :
  TestProject(
    name = name,
    group = group,
    version = version,
    latestReleasedVersion = latestReleasedVersion,
    projectDependencies = projectDependencies,
    externalDependencies = externalDependencies,
    plugins =
      """
      id("firebase-${if (libraryType == LibraryType.JAVA) "java-" else ""}library")
    """
        .trimIndent(),
    libraryType = libraryType,
  ) {
  override fun generateBuildFile(): String {
    return """
      ${super.generateBuildFile()}
      firebaseLibrary {
        ${libraryGroup?.let { "libraryGroup = \"$it\"" } ?: ""}
        ${customizePom?.let { "customizePom {$it}" } ?: ""}
        publishJavadoc = $publishJavadoc
      }
    """
      .trimIndent()
  }
}

/** Creates an [ArtifactDependency] for a test. */
fun testArtifact(
  groupId: String,
  artifactId: String,
  version: String?,
  type: LibraryType = LibraryType.JAVA,
  scope: String = "runtime",
) = ArtifactDependency(groupId, artifactId, version, type.format, scope)

val defaultLicense =
  LicenseElement(
    "The Apache Software License, Version 2.0",
    "http://www.apache.org/licenses/LICENSE-2.0.txt",
  )

data class Pom(
  val artifact: ArtifactDependency,
  val license: LicenseElement = defaultLicense,
  val dependencies: List<ArtifactDependency> = listOf(),
) {
  companion object {
    fun parse(file: File): Pom {
      val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
      val pomElement = PomElement.fromElement(document.documentElement)

      val groupId = pomElement.groupId
      val artifactId = pomElement.artifactId
      val version = pomElement.version
      val type = pomElement.packaging?.let { LibraryType.fromFormat(it) } ?: LibraryType.JAVA
      val license = pomElement.licenses.firstOrNull() ?: defaultLicense
      val dependencies = pomElement.dependencies

      return Pom(testArtifact(groupId, artifactId, version, type), license, dependencies)
    }
  }
}

/** Converts a [FirebaseTestProject] to an [ArtifactDependency]. */
fun TestProject.toArtifact() = testArtifact(group, name, version, libraryType)

/**
 * Converts a [FirebaseTestProject] to a gradle dependency string.
 *
 * For example:
 * ```
 * val myProject = Project(name = "firestore", group = "com.firebase.google", version = "1.0.0")
 *
 * println(myProject.toDependency()) // "com.google.firebase.google:firestore:1.0.0"
 * println(myProject.toDependency(true)) // project(":firestore")
 * ```
 *
 * @param projectLevel whether the dependency should be a project level dependency or external
 * @see toArtifact
 */
fun TestProject.toDependency(projectLevel: Boolean = false) =
  if (projectLevel) "project(\"${path}\")" else "\"${toArtifact().simpleDepString}\""
