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
  id("com.android.library")
  kotlin("android")
}

android {
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  compileSdk = targetSdkVersion
  namespace = "com.google.firebase.testing.integ"

  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

dependencies {
  implementation("com.google.firebase:firebase-common:20.3.2")
  implementation("com.google.firebase:firebase-components:17.1.0")

  implementation(libs.junit)
  implementation(libs.androidx.test.runner)
  implementation(libs.kotlin.coroutines.test)
}
