/*
 * Copyright 2019 Google LLC
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

plugins { id("firebase-library") }

firebaseLibrary {
  publishJavadoc = false
  releaseNotes { enabled.set(false) }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  adbOptions { timeOutInMs = 60000 }

  namespace = "com.google.firebase.datatransport"
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

  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  api(libs.firebase.common)
  api(libs.firebase.components)

  implementation(libs.androidx.annotation)
  implementation("com.google.android.datatransport:transport-api:3.1.0")
  implementation("com.google.android.datatransport:transport-backend-cct:3.2.0")
  implementation("com.google.android.datatransport:transport-runtime:3.2.0")
  implementation(libs.kotlin.stdlib.jdk8)

  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
}
