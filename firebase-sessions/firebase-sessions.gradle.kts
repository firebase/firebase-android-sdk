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

plugins {
  id("firebase-library")
  id("kotlin-android")
}

firebaseLibrary {
  testLab.enabled = true
  publishSources = true
}

android {
  val targetSdkVersion: Int by rootProject
  compileSdk = 33
  defaultConfig {
    minSdk = 16
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation("com.google.firebase:firebase-common-ktx:20.3.2")
  implementation("com.google.firebase:firebase-components:17.1.0")
  implementation("com.google.firebase:firebase-encoders-json:18.0.1")
  implementation("com.google.firebase:firebase-encoders:17.0.0")
  implementation("com.google.firebase:firebase-installations-interop:17.1.0")
  implementation("com.google.android.datatransport:transport-api:3.0.0")
  implementation ("androidx.datastore:datastore-preferences:1.0.0")
  implementation(libs.androidx.annotation)

  runtimeOnly("com.google.firebase:firebase-installations:17.1.3")
  runtimeOnly(project(":firebase-datatransport"))

  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}
