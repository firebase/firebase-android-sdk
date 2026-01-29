@file:Suppress("DEPRECATION") // App projects should still use FirebaseTestLabPlugin.
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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
  namespace = "com.google.firebase.testing.processor"
  compileSdk = 36
  defaultConfig {
    applicationId = "com.google.firebase.testing.processor"
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }

dependencies {
  implementation(project(":firebase-ai"))
  ksp(project(":firebase-ai-ksp-processor"))

  implementation("com.google.firebase:firebase-common:22.0.0")
  implementation(libs.firebase.components)

  implementation("com.google.android.gms:play-services-basement:18.1.0")
  implementation("com.google.android.gms:play-services-tasks:18.0.1")

  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.core:core-ktx:1.9.0")
  implementation("com.google.android.material:material:1.8.0")

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
