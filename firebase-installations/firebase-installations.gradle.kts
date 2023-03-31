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

plugins { id("firebase-library") }

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
  implementation("com.google.firebase:firebase-common:20.3.2")
  implementation("com.google.firebase:firebase-installations-interop:17.1.0")
  implementation("com.google.firebase:firebase-components:17.1.0")
  implementation("com.google.firebase:firebase-annotations:16.2.0")

  implementation(libs.androidx.multidex)
  implementation(libs.playservices.tasks)

  compileOnly(libs.autovalue.annotations)
  annotationProcessor(libs.autovalue)

  testImplementation(project(":integ-testing"))
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.inline)

  androidTestImplementation(project(":integ-testing"))
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.annotation)
  androidTestImplementation(libs.mockito.core)
  androidTestImplementation(libs.mockito.inline)
}
