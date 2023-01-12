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
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
  implementation(project(":firebase-annotations"))
  implementation(project(":firebase-common"))
  implementation(project(":firebase-components"))
  implementation(project(":appcheck:firebase-appcheck-interop"))
  implementation(libs.playservices.base)
  implementation(libs.playservices.tasks)

  javadocClasspath(libs.autovalue.annotations)

  testImplementation(project(":integ-testing"))
  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.rules)

  androidTestImplementation(project(":appcheck:firebase-appcheck"))
  androidTestImplementation(project(":integ-testing"))
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.annotation)
  androidTestImplementation(libs.mockito.core)
  androidTestImplementation(libs.mockito.inline)
}
