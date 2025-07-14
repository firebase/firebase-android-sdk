/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.firebase.dataconnect.gradle.plugin.UpdateDataConnectExecutableVersionsTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("com.android.library")
  id("kotlin-android")
  alias(libs.plugins.kotlinx.serialization)
  id("com.google.firebase.dataconnect.gradle.plugin")
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect.connectors"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }

  @Suppress("UnstableApiUsage")
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }

  dataconnect {
    configDir = file("../emulator/dataconnect")
    codegen { connectors = listOf("demo", "keywords") }
  }
}

dependencies {
  implementation(project(":firebase-dataconnect"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)

  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.property)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotest.assertions)
  androidTestImplementation(libs.kotest.property)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.testonly.three.ten.abp)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.truth.liteproto.extension)
  androidTestImplementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions { freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn") }
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinCompile>().all {
  if (!name.contains("test", ignoreCase = true)) {
    if (!kotlinOptions.freeCompilerArgs.contains("-Xexplicit-api=strict")) {
      kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}

// Adds a Gradle task that updates the JSON file that stores the list of Data Connect
// executable versions.
//
// Example 1: Add versions 1.4.3 and 1.4.4 to the JSON file, and set 1.4.4 as the default:
//   ../../gradlew -Pversions=1.4.3,1.4.4 -PdefaultVersion=1.4.4 updateJson --info
//
// Example 2: Add version 1.2.3 to the JSON file, but do not change the default version:
//   ../../gradlew -Pversion=1.2.3 updateJson --info
//
// The `--info` argument can be omitted; it merely controls the level of log output.
tasks.register<UpdateDataConnectExecutableVersionsTask>("updateJson") {
  outputs.upToDateWhen { false }
  jsonFile.set(
    project.layout.projectDirectory.file(
      "../gradleplugin/plugin/src/main/resources/com/google/firebase/dataconnect/gradle/" +
        "plugin/DataConnectExecutableVersions.json"
    )
  )
  workDirectory.set(project.layout.buildDirectory.dir("updateJson"))

  val propertyNames =
    object {
      val version = "version"
      val versions = "versions"
      val updateMode = "updateMode"
      val defaultVersion = "defaultVersion"
    }

  val singleVersion: String? = project.providers.gradleProperty(propertyNames.version).orNull
  val multipleVersions: List<String>? =
    project.providers.gradleProperty(propertyNames.versions).orNull?.split(',')
  versions.set(
    buildList {
      singleVersion?.let { add(it) }
      multipleVersions?.let { addAll(it) }
    }
  )

  doFirst {
    if (versions.get().isEmpty()) {
      logger.warn(
        "WARNING: no '${propertyNames.version}' or '${propertyNames.versions}' specified " +
          "for task '$name'; no versions will be added to ${jsonFile.get()}. " +
          "Try specifying something like '-P${propertyNames.version}=1.2.3' or " +
          "'-P${propertyNames.versions}=1.2.3,4.5.6' on the gradle command line " +
          "if you want to add versions (warning code bm6d5ezxzd)"
      )
    }
  }

  updateMode.set(
    project.providers.gradleProperty(propertyNames.updateMode).map {
      when (it) {
        "overwrite" -> UpdateDataConnectExecutableVersionsTask.UpdateMode.Overwrite
        "update" -> UpdateDataConnectExecutableVersionsTask.UpdateMode.Update
        else ->
          throw Exception(
            "Invalid '${propertyNames.updateMode}' specified for task '$name': $it. " +
              "Valid values are 'update' and 'overwrite'. " +
              "Try specifying '-P${propertyNames.updateMode}=update' or " +
              "'-P${propertyNames.updateMode}=overwrite' on the gradle command line. " +
              "(error code v2e3cfqbnf)"
          )
      }
    }
  )

  doFirst {
    if (!updateMode.isPresent) {
      logger.warn(
        "WARNING: no '${propertyNames.updateMode}' specified for task '$name'; " +
          "the default update mode of 'update' will be used when updating ${jsonFile.get()}. " +
          "Try specifying '-P${propertyNames.updateMode}=update' or " +
          "'-P${propertyNames.updateMode}=overwrite' on the gradle command line " +
          "if you want a different update mode, or just want to be explicit about " +
          "which update mode is in effect (warning code tjyscqmdne)"
      )
    }
  }

  defaultVersion.set(project.providers.gradleProperty(propertyNames.defaultVersion))

  doFirst {
    if (!defaultVersion.isPresent) {
      logger.warn(
        "WARNING: no '${propertyNames.defaultVersion}' specified for task '$name'; " +
          "the default version will not be updated in ${jsonFile.get()}. " +
          "Try specifying something like '-P${propertyNames.defaultVersion}=1.2.3' " +
          "on the gradle command line if you want to update the default version " +
          "(warning code vqrbrktx9f)"
      )
    }
  }
}
