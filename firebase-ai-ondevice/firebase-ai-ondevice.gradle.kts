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

@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("firebase-library")
  id("kotlin-android")
}

firebaseLibrary {
  testLab.enabled = false
  publishJavadoc = true
  releaseNotes {
    name.set("{{firebase_ai_logic_ondevice}}")
    versionName.set("ai_ondevice")
  }
}

android {
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.ai.ondevice"
  compileSdk = 34
  defaultConfig {
    minSdk = 26 // Required by mlkit genai prompt sdk
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildTypes { release { isMinifyEnabled = false } }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions {
    targetSdk = targetSdkVersion
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }
  lint {
    targetSdk = targetSdkVersion
    baseline = file("lint-baseline.xml")
  }
  sourceSets { getByName("test").java.srcDirs("src/testUtil") }
}

kotlin {
  compilerOptions { jvmTarget = JvmTarget.JVM_1_8 }
  explicitApi()
}

dependencies {
  implementation(libs.genai.prompt)
  implementation(project(":firebase-ai-ondevice-interop"))

  implementation(libs.firebase.common)
  implementation(libs.firebase.components)
  implementation(libs.firebase.annotations)
  implementation(libs.androidx.annotation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.core.ktx)
  implementation(libs.slf4j.nop)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.reactive)

  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.assertions.json)
  testImplementation(libs.org.json)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockk)

  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}
