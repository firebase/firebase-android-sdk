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
  id("copy-google-services")
}

firebaseLibrary {
  libraryGroup = "database"
  testLab.enabled = true
  releaseNotes {
    name.set("{{database}}")
    versionName.set("realtime-database")
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  installation.timeOutInMs = 60 * 1000
  compileSdk = compileSdkVersion

  namespace = "com.google.firebase.database"
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes { release { isMinifyEnabled = false } }

  sourceSets {
    named("androidTest") { java.srcDir("src/testUtil/java") }
    named("test") { java.srcDir("src/testUtil") }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }

    packagingOptions.resources.excludes += "META-INF/DEPENDENCIES"

    testOptions {
      targetSdk = targetSdkVersion
      unitTests { isIncludeAndroidResources = true }
    }
    lint { targetSdk = targetSdkVersion }
  }
}

dependencies {
  api("com.google.firebase:firebase-appcheck-interop:17.1.0")
  api(libs.firebase.common)
  api(libs.firebase.components)
  api("com.google.firebase:firebase-auth-interop:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  api("com.google.firebase:firebase-database-collection:18.0.1")
  implementation(libs.androidx.annotation)
  implementation(libs.bundles.playservices)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.core)
  api(libs.playservices.tasks)

  testImplementation(libs.jackson.core)
  testImplementation(libs.jackson.databind)
  testImplementation("com.firebase:firebase-token-generator:2.0.0")
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.rules)
  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.quickcheck)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)

  androidTestImplementation(libs.jackson.core)
  androidTestImplementation(libs.jackson.databind)
  androidTestImplementation(libs.hamcrest)
  androidTestImplementation(libs.hamcrest.library)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.quickcheck)
  androidTestImplementation(libs.truth)
}
