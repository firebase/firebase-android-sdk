import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright 2026 Google LLC
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
  libraryGroup = "crashlytics"

  testLab.enabled = false
  publishJavadoc = false

  releaseNotes { enabled = false }
}

android {
  val compileSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.crashlytics.telemetry.okhttp"
  compileSdk = compileSdkVersion

  defaultConfig { minSdk = minSdkVersion }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  testOptions {
    unitTests.isReturnDefaultValues = true
    unitTests.all { testTask -> testTask.useJUnitPlatform() }
  }
}

kotlin {
  explicitApi()
  compilerOptions { jvmTarget = JvmTarget.JVM_1_8 }
}

dependencies {
  api(project(":firebase-crashlytics-telemetry"))

  compileOnly(libs.okhttp)

  implementation(platform(libs.opentelemetry.bom))
  implementation(libs.opentelemetry.api)
  implementation(libs.opentelemetry.semconv)

  testImplementation(libs.okhttp)
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.opentelemetry.sdk)
  testImplementation(libs.opentelemetry.sdk.testing)
  testImplementation(libs.truth)

  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)
}
