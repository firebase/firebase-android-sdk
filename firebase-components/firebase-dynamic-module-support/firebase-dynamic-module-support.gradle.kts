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

plugins { id("firebase-library") }

group = "com.google.firebase"

firebaseLibrary {
  testLab.enabled = false
  publishJavadoc = false
  releaseNotes {
    name.set("Dynamic feature modules support")
    versionName.set("dynamic-feature-modules-support")
    hasKTX.set(false)
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject
  compileSdk = compileSdkVersion
  namespace = "com.google.firebase.dynamicloading"
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }
  lint { targetSdk = targetSdkVersion }
}

dependencies {
  implementation(libs.feature.delivery)
  implementation(libs.kotlin.stdlib.jdk8)
  api(libs.firebase.common)
  api(libs.firebase.components)
}
