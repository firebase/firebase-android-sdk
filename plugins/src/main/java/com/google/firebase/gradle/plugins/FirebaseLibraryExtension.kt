// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension
import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.maven.MavenPom
import org.gradle.kotlin.dsl.create

/**
 * Configuration class for Firebase Libraries.
 *
 * Defines configurable settings for Firebase Library SDKs, which can be configured in their gradle
 * files.
 *
 * ```kts
 * plugins {
 *   id("firebase-library")
 * }
 *
 * firebaseLibrary {
 *     libraryGroup = "firestore"
 *     testLab {
 *         enabled = true
 *         timeout = '45m'
 *     }
 *     releaseNotes {
 *         name.set("{{firestore}}")
 *         versionName.set("firestore")
 *     }
 * }
 * ```
 *
 * @see [FirebaseAndroidLibraryPlugin]
 * @see [FirebaseJavaLibraryPlugin]
 */
// TODO(b/372730478): Ensure downstream usage of properties in done is a lazy manner
abstract class FirebaseLibraryExtension
@Inject
constructor(val project: Project, val type: LibraryType) {
  /**
   * Publish Javadocs/KDocs for this library.
   *
   * Documentation is generated via the [DackkaPlugin], and can be manually invoked with the task
   * `kotlindoc`.
   *
   * Defaults to `true`.
   *
   * ```sh
   * ./gradlew :firebase-common:kotlindoc
   * ```
   *
   * @see [FirebaseLibraryExtension]
   */
  abstract val publishJavadoc: Property<Boolean>

  /**
   * Only publish Kotlindocs instead of also publishing Javadocs for this library.
   *
   * Defaults to `false`.
   *
   * ```sh
   * ./gradlew :firebase-common:kotlindoc
   * ```
   *
   * @see [FirebaseLibraryExtension]
   * @see publishJavadoc
   */
  abstract val onlyPublishKotlindoc: Property<Boolean>

  /**
   * Indicates the library is in a preview mode (such as `alpha` or `beta`).
   *
   * Setting a [previewMode] will cause a descriptive error to be thrown if you attempt to release
   * the library without the [previewMode] provided as a suffix to your version in
   * `gradle.properties`.
   *
   * This can help prevent yourself from accidentally releasing a library before it's ready.
   *
   * ```kt
   * firebaseLibrary {
   *   previewMode = "alpha"
   * }
   * ```
   * ```properties
   * # gradle.properties
   * version=16.0.0-alpha
   * ```
   *
   * @see [FirebaseLibraryExtension]
   */
  abstract val previewMode: Property<String>

  /**
   * The group for which this library is published under.
   *
   * Unless the group of your package is separate from your published group, you don't need to
   * configure this.
   *
   * Defaults to the project's `group`.
   *
   * ```kt
   * firebaseLibrary {
   *   groupId = "com.google.firebase"
   * }
   * ```
   *
   * @see [FirebaseLibraryExtension]
   */
  abstract val groupId: Property<String>

  /**
   * The name of the artifact for which this library is published under.
   *
   * Unless the name of your gradle project is different than the name you publish under, you don't
   * need to configure this.
   *
   * Defaults to the project's `name`.
   *
   * ```kt
   * firebaseLibrary {
   *   artifactId = "firebase-common"
   * }
   * ```
   *
   * @see [FirebaseLibraryExtension]
   */
  abstract val artifactId: Property<String>

  /**
   * An internal name to signal that this library is linked with another.
   *
   * Libraries with the same [libraryGroup] are released together. This also forces all libraries in
   * the group to _share_ the same version.
   *
   * Libraries in the same group are also allowed to _always_ have a project level dependency on one
   * another- meaning they won't be changed to pinned dependencies after a release.
   *
   * ```kt
   * firebaseLibrary {
   *   libraryGroup = "messaging"
   *   artifactId = "firebase-messaging"
   * }
   * ```
   * ```kt
   * firebaseLibrary {
   *   libraryGroup = "messaging"
   *   artifactId = "firebase-messaging-directboot"
   * }
   * ```
   *
   * @see [FirebaseLibraryExtension]
   */
  abstract val libraryGroup: Property<String>

  /**
   * A collection of projects to bind lint checks from.
   *
   * Projects specified will be included in the output for related `lintChecks` tasks on this
   * library.
   *
   * Typically only used to enabled `firebase-lint`, but can be used to configure custom linting as
   * well.
   *
   * Defaults to `:tools:lint`
   *
   * ```kt
   * firebaseLibrary {
   *   androidLintCheckProjects = listOf(":tools:lint")
   * }
   * ```
   *
   * Can also be specified via a gradle property with `firebase.checks.lintProjects`.
   *
   * ```
   * ./gradlew :firebase-common:build -Pfirebase.checks.lintProjects=":tools:lint,:custom:lint"
   * ```
   *
   * @see [FirebaseLibraryExtension]
   */
  abstract val androidLintCheckProjects: SetProperty<String>

  /**
   * Firebase Test Lab configuration.
   *
   * @see [FirebaseTestLabExtension]
   * @see [FirebaseLibraryExtension]
   */
  val testLab: FirebaseTestLabExtension =
    project.objects.newInstance(FirebaseTestLabExtension::class.java)

  /**
   * Configurations for generated release notes.
   *
   * @see [ReleaseNotesConfigurationExtension]
   * @see [FirebaseLibraryExtension]
   */
  val releaseNotes: ReleaseNotesConfigurationExtension =
    project.extensions.create<ReleaseNotesConfigurationExtension>("releaseNotes")

  /**
   * Configurable action to apply when generating a pom file for a release.
   *
   * @see [customizePom]
   * @hide
   */
  internal var customizePomAction: Action<MavenPom> = Action {}
    private set

  /**
   * Firebase Test Lab configuration.
   *
   * @see [FirebaseTestLabExtension]
   * @see [FirebaseLibraryExtension]
   */
  fun testLab(action: Action<FirebaseTestLabExtension>) {
    action.execute(testLab)
  }

  /**
   * Apply custom configurations for pom generation.
   *
   * @see [customizePomAction]
   * @see [FirebaseLibraryExtension]
   */
  fun customizePom(action: Action<MavenPom>) {
    customizePomAction = action
  }

  /**
   * Configurations for generated release notes.
   *
   * @see [ReleaseNotesConfigurationExtension]
   * @see [FirebaseLibraryExtension]
   */
  fun releaseNotes(action: Action<ReleaseNotesConfigurationExtension>) {
    action.execute(releaseNotes)
  }

  val version: String
    get() = project.version.toString()

  val previousVersion: String
    get() = project.properties["latestReleasedVersion"].toString()

  val path: String = project.path

  val runtimeClasspath: String =
    if (type == LibraryType.ANDROID) "releaseRuntimeClasspath" else "runtimeClasspath"

  override fun toString(): String {
    return """FirebaseLibraryExtension{name="$mavenName", project="$path", type="$type"}"""
  }
}
