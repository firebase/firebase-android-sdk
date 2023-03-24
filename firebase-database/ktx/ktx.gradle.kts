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
  kotlin("android")
}

group = "com.google.firebase"

firebaseLibrary {
  releaseWith(project(":firebase-database"))
  publishJavadoc = true
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
  }

  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.core)

  implementation("com.google.firebase:firebase-common:20.3.1")
  implementation("com.google.firebase:firebase-components:17.1.0")
  implementation("com.google.firebase:firebase-common-ktx:20.3.1")
  implementation(project(":firebase-database"))
  implementation("androidx.annotation:annotation:1.1.0")
  implementation("com.google.android.gms:play-services-tasks:18.0.1")

  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation("junit:junit:4.12")
  testImplementation("androidx.test:core:1.2.0")

  androidTestImplementation("junit:junit:4.12")
  androidTestImplementation("androidx.test:runner:1.2.0")
}
