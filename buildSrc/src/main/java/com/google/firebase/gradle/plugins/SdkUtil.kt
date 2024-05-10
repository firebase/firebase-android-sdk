/*
 * Copyright 2019 Google LLC
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

import com.android.build.gradle.LibraryExtension
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.StopActionException

val Project.sdkDir: File
  get() {
    val properties = Properties()
    val localProperties = rootProject.file("local.properties")
    if (localProperties.exists()) {
      try {
        FileInputStream(localProperties).use { fis -> properties.load(fis) }
      } catch (ex: IOException) {
        throw GradleException("Could not load local.properties", ex)
      }
    }
    val sdkDir = properties.getProperty("sdk.dir")
    if (sdkDir != null) {
      return file(sdkDir)
    }
    val androidHome =
      System.getenv("ANDROID_HOME") ?: throw GradleException("No sdk.dir or ANDROID_HOME set.")
    return file(androidHome)
  }

val Project.androidJar: File?
  get() {
    val android = project.extensions.findByType(LibraryExtension::class.java) ?: return null
    return File(sdkDir, String.format("/platforms/%s/android.jar", android.compileSdkVersion))
  }

/**
 * Provides default text for releasing KTX libs that are transitively invoked in a release, because
 * their parent module is releasing. This only applies to `-ktx` libs, not Kotlin SDKs.
 */
fun KTXTransitiveReleaseText(projectName: String) =
  """
      |The Kotlin extensions library transitively includes the updated
      |`${ProjectNameToKTXPlaceholder(projectName)}` library. The Kotlin extensions library has no additional
      |updates.
    """
    .trimMargin()
    .trim()

/**
 * Maps a project's name to a KTX suitable placeholder.
 *
 * Some libraries produce artifacts with different coordinates than their project name. This method
 * helps to map that gap for [KTXTransitiveReleaseText].
 */
fun ProjectNameToKTXPlaceholder(projectName: String) =
  when (projectName) {
    "firebase-perf" -> "firebase-performance"
    "firebase-appcheck" -> "firebase-appcheck"
    else -> projectName
  }

/**
 * Provides extra metadata needed to create release notes for a given project.
 *
 * This data is needed for g3 internal mappings, and does not really have any implications for
 * public repo actions.
 *
 * @property name The variable name for a project in a release note
 * @property vesionName The variable name given to the versions of a project
 * @property hasKTX The module has a KTX submodule (not to be confused with having KTX files)
 * @see MakeReleaseNotesTask
 */
data class ReleaseNotesMetadata(
  val name: String,
  val versionName: String,
  val hasKTX: Boolean = true
)

/**
 * Maps the name of a project to its potential [ReleaseNotesMetadata].
 *
 * @throws StopActionException If a mapping is not found
 */
// TODO() - Should we expose these as firebaselib configuration points; especially for new SDKS?
fun convertToMetadata(string: String) =
  when (string) {
    "firebase-abt" -> ReleaseNotesMetadata("{{ab_testing}}", "ab_testing", false)
    "firebase-appdistribution" -> ReleaseNotesMetadata("{{appdistro}}", "app-distro", false)
    "firebase-appdistribution-api" -> ReleaseNotesMetadata("{{appdistro}} API", "app-distro-api")
    "firebase-config" -> ReleaseNotesMetadata("{{remote_config}}", "remote-config")
    "firebase-crashlytics" -> ReleaseNotesMetadata("{{crashlytics}}", "crashlytics")
    "firebase-crashlytics-ndk" ->
      ReleaseNotesMetadata("{{crashlytics}} NDK", "crashlytics-ndk", false)
    "firebase-database" -> ReleaseNotesMetadata("{{database}}", "realtime-database")
    "firebase-dynamic-links" -> ReleaseNotesMetadata("{{ddls}}", "dynamic-links")
    "firebase-firestore" -> ReleaseNotesMetadata("{{firestore}}", "firestore")
    "firebase-functions" -> ReleaseNotesMetadata("{{functions_client}}", "functions-client")
    "firebase-dynamic-module-support" ->
      ReleaseNotesMetadata(
        "Dynamic feature modules support",
        "dynamic-feature-modules-support",
        false
      )
    "firebase-inappmessaging" -> ReleaseNotesMetadata("{{inappmessaging}}", "inappmessaging")
    "firebase-inappmessaging-display" ->
      ReleaseNotesMetadata("{{inappmessaging}} Display", "inappmessaging-display")
    "firebase-installations" -> ReleaseNotesMetadata("{{firebase_installations}}", "installations")
    "firebase-messaging" -> ReleaseNotesMetadata("{{messaging_longer}}", "messaging")
    "firebase-messaging-directboot" ->
      ReleaseNotesMetadata("Cloud Messaging Direct Boot", "messaging-directboot", false)
    "firebase-ml-modeldownloader" ->
      ReleaseNotesMetadata("{{firebase_ml}}", "firebaseml-modeldownloader")
    "firebase-perf" -> ReleaseNotesMetadata("{{perfmon}}", "performance")
    "firebase-storage" -> ReleaseNotesMetadata("{{firebase_storage_full}}", "storage")
    "firebase-appcheck" -> ReleaseNotesMetadata("{{app_check}}", "appcheck")
    "firebase-appcheck-debug" ->
      ReleaseNotesMetadata("{{app_check}} Debug", "appcheck-debug", false)
    "firebase-appcheck-debug-testing" ->
      ReleaseNotesMetadata("{{app_check}} Debug Testing", "appcheck-debug-testing", false)
    "firebase-appcheck-playintegrity" ->
      ReleaseNotesMetadata("{{app_check}} Play integrity", "appcheck-playintegrity", false)
    else -> throw StopActionException("No metadata mapping found for project: $string")
  }
