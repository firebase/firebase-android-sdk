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

@file:Suppress("UnstableApiUsage")

plugins {
  id("firebase-library")
  id("firebase-vendor")
  id("kotlin-android")
  alias(libs.plugins.kapt)
}

firebaseLibrary {
  libraryGroup = "crashlytics"

  testLab.enabled = true
  publishJavadoc = false
  releaseNotes { enabled.set(false) }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.sessions"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }
  lint { targetSdk = targetSdkVersion }
}

tasks.withType(org.jetbrains.kotlin.gradle.tasks.KaptGenerateStubs::class.java).configureEach {
  kotlinOptions.jvmTarget = "1.8"
}

dependencies {
  api("com.google.firebase:firebase-common:21.0.0")
  api("com.google.firebase:firebase-common-ktx:21.0.0")

  api("com.google.firebase:firebase-components:18.0.0")
  api("com.google.firebase:firebase-installations-interop:17.2.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }

  api("com.google.firebase:firebase-annotations:16.2.0")
  api("com.google.firebase:firebase-encoders:17.0.0")
  api("com.google.firebase:firebase-encoders-json:18.0.1")

  implementation("com.google.android.datatransport:transport-api:3.2.0")
  implementation(libs.javax.inject)
  implementation(libs.androidx.annotation)
  implementation(libs.androidx.datastore.preferences)

  vendor(libs.dagger.dagger) { exclude(group = "javax.inject", module = "javax.inject") }

  compileOnly(libs.errorprone.annotations)

  runtimeOnly("com.google.firebase:firebase-installations:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  runtimeOnly("com.google.firebase:firebase-datatransport:19.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }

  kapt(project(":encoders:firebase-encoders-processor"))
  kapt(libs.dagger.compiler)

  testImplementation(project(":integ-testing")) {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}
