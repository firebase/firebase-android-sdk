// Copyright 2023 Google LLC
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
  id("kotlin-android")
  kotlin("android")
}

group = "com.google.firebase"

firebaseLibrary {
  libraryGroup("database")
  publishJavadoc = false
  publishReleaseNotes = false
  publishSources = true
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  compileSdk = compileSdkVersion

  namespace = "com.google.firebase.database.ktx"
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  sourceSets {
    named("main") { java.srcDir("src/main/kotlin") }
    named("test") { java.srcDir("src/test/kotlin") }
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api("com.google.firebase:firebase-common:21.0.0")
    api("com.google.firebase:firebase-common-ktx:21.0.0")
    api(project(":firebase-database"))

    implementation("com.google.firebase:firebase-components:18.0.0")

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
}
