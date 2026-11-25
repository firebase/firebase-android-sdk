/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  id("firebase-library")
  alias(libs.plugins.protobuf)
}

firebaseLibrary {
  libraryGroup = "transport"
  publishJavadoc = false
  releaseNotes { enabled.set(false) }
}

protobuf {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "${libs.protoc.get()}"
  }
  generateProtoTasks { all().forEach { task -> task.builtins { maybeCreate("java") } } }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.android.datatransport.backend.cct"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("proguard.txt")
  }
  testOptions { targetSdk = targetSdkVersion }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

dependencies {
  api("com.google.android.datatransport:transport-api:5.0.0")
  api(project(":transport:transport-runtime"))
  api("com.google.firebase:firebase-encoders:18.0.0")
  api("com.google.firebase:firebase-encoders-json:19.0.0")

  implementation("androidx.annotation:annotation:1.3.0")

  compileOnly(libs.autovalue.annotations)

  annotationProcessor(project(":encoders:firebase-encoders-processor"))
  annotationProcessor("com.google.auto.value:auto-value:1.6.5")

  testImplementation(libs.androidx.test.core)
  testImplementation("com.github.tomakehurst:wiremock:3.0.1")
  testImplementation(libs.protobuf.java.util)
  testImplementation(libs.truth)
  testImplementation(libs.truth.proto.extension)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)

  androidTestImplementation("androidx.test:runner:1.2.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.2.0")
}
