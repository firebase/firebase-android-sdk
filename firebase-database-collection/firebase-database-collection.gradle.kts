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

firebaseLibrary {
  publishSources = true
  publishJavadoc = false
  publishReleaseNotes = false
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  compileSdk = compileSdkVersion

  namespace = "com.google.firebase.database.collection"
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
  implementation(libs.playservices.base)

  testAnnotationProcessor("net.java:quickcheck-src-generator:0.6")
  testAnnotationProcessor("net.java.quickcheck:quickcheck-src-generator:0.6")

  testImplementation(libs.junit)
  testImplementation(libs.quickcheck)
}
