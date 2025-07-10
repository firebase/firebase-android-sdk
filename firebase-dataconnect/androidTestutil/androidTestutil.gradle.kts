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
  id("com.android.library")
  id("kotlin-android")
  alias(libs.plugins.kotlinx.serialization)
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect.androidTestutil"
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

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }
}

dependencies {
  implementation(project(":firebase-dataconnect"))
  implementation(project(":firebase-dataconnect:testutil"))

  implementation("com.google.firebase:firebase-auth:22.3.1")
  implementation(project(":appcheck:firebase-appcheck"))

  implementation(libs.androidx.test.core)
  implementation(libs.androidx.test.junit)
  implementation(libs.auth0.jwt)
  implementation(libs.kotest.assertions)
  implementation(libs.kotest.property)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.truth)
  implementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions { freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn") }
}
