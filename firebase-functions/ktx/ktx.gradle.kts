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
  id("kotlin-android")
}

firebaseLibrary {
  releaseWith(project(":firebase-functions"))
  publishJavadoc = true
  publishSources = true
  testLab.enabled = true
}

android {
  val targetSdkVersion : Int by rootProject

  compileSdk = targetSdkVersion
  defaultConfig {
    minSdk = 16
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  sourceSets {
    getByName("main").java.srcDirs("src/main/kotlin")
    getByName("test").java.srcDirs("src/test/kotlin")
    getByName("androidTest").java.srcDirs("src/androidTest/kotlin")
  }
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":firebase-common"))
  implementation(project(":firebase-components"))
  implementation(project(":firebase-common:ktx"))
  implementation(project(":firebase-functions"))
  implementation(libs.kotlin.stdlib)
  implementation(libs.androidx.annotation)
  implementation(libs.playservices.tasks)

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.androidx.test.runner)

  testImplementation(libs.robolectric)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.androidx.test.core)
}
