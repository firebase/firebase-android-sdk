// Copyright 2022 Google LLC
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

plugins {
  id("firebase-library")
}

firebaseLibrary {
  testLab.enabled = true
  publishSources = true
}

android {
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  compileSdk = targetSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    testInstrumentationRunnerArguments["firebaseAppCheckDebugSecret"] =
      System.getenv("FIREBASE_APP_CHECK_DEBUG_SECRET") ?: ""
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":firebase-common"))
  implementation(project(":firebase-components"))
  implementation(project(":appcheck:firebase-appcheck"))
  implementation(project(":appcheck:firebase-appcheck-debug"))
  implementation(project(":appcheck:firebase-appcheck-interop"))
  implementation(libs.playservices.base)
  implementation(libs.playservices.tasks)
  implementation(libs.androidx.test.core)

  javadocClasspath(libs.autovalue.annotations)

  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
  testImplementation(libs.androidx.test.core)
  testImplementation(project(":appcheck:firebase-appcheck-safetynet"))

  androidTestImplementation(project(":firebase-storage"))
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.mockito.core)
}

// ==========================================================================
// Copy from here down if you want to use the google-services plugin in your
// androidTest integration tests.
// ==========================================================================
extra["packageName"] = "com.google.firebase.appcheck.debug.testing"
apply(from = "../../gradle/googleServices.gradle")
