/*
 * Copyright 2026 Google LLC
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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("firebase-library")
  id("kotlin-android")
  id("org.jetbrains.kotlin.plugin.serialization")
  id("copy-google-services")
}

firebaseLibrary {
  libraryGroup = "sql"
  testLab.enabled = false
  publishJavadoc = true
  onlyPublishKotlindoc = true
  // No release notes before the launch. At launch, replace `enabled = false` with the `name` and
  // `versionName` of Firebase SQL in G3.
  releaseNotes { enabled = false }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.sql"

  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // CloudSqlEndToEndTest calls the service that these Gradle properties name, for example
    // `./gradlew :firebase-sql:connectedCheck -PtargetServiceId=my-service
    // -PtargetLocation=us-east4`.
    val targetServiceId = findProperty("targetServiceId") ?: ""
    buildConfigField("String", "TARGET_SERVICE_ID", "\"$targetServiceId\"")
    val targetLocation = findProperty("targetLocation") ?: ""
    buildConfigField("String", "TARGET_LOCATION", "\"$targetLocation\"")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  // Robolectric reads the merged manifest, so that FirebaseApp discovers FirebaseSqlRegistrar and
  // the unit tests can go through FirebaseSql.getInstance().
  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }
  lint { targetSdk = targetSdkVersion }
}

kotlin {
  // Every public declaration must state its visibility, so that nothing becomes public API by
  // accident.
  explicitApi()
  compilerOptions {
    jvmTarget = JvmTarget.JVM_1_8
    // The public interfaces require this opt-in to be implemented, and the SDK implements them.
    optIn.add("com.google.firebase.annotations.InternalForInheritanceFirebaseApi")
  }
}

dependencies {
  api(libs.firebase.common)
  // InternalForInheritanceFirebaseApi, the opt-in that implementing the public interfaces
  // requires. It is part of the public API, so it is an api dependency.
  api(project(":firebase-annotations"))
  // SerializationStrategy and DeserializationStrategy, which the public API takes.
  api(libs.kotlinx.serialization.core)
  implementation(libs.firebase.components)

  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.okhttp)

  // The interfaces of Firebase Authentication and App Check, which the SDK uses to send their
  // tokens. They are needed at runtime even when the app links neither SDK, so they are not
  // compileOnly.
  implementation(project(":appcheck:firebase-appcheck-interop"))
  implementation(libs.firebase.auth.interop)

  testImplementation(libs.junit)
  testImplementation(libs.mockwebserver)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.junit)
}
