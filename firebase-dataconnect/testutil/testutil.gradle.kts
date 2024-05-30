/*
 * Copyright 2024 Google LLC
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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("firebase-library")
  id("kotlin-android")
  id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  namespace = "com.google.firebase.dataconnect.testutil"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
}

dependencies {
  api(project(":firebase-dataconnect"))
  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.core)

  implementation("com.google.firebase:firebase-components:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
  }
  implementation("com.google.firebase:firebase-auth:22.3.1") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }

  implementation(libs.mockito.core)
  implementation(libs.robolectric)
  implementation(libs.truth)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions {
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}