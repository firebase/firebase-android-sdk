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
  publishJavadoc = false
  releaseNotes {
    name.set("{{firebase_ai_logic_ondevice_interop}}")
    versionName.set("ai_ondevice_interop")
  }
}

android {
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.ai.ondevice.interop"
  compileSdk = 34
  defaultConfig {
    minSdk = minSdkVersion
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
  api(libs.firebase.common)
  implementation(libs.kotlinx.coroutines.android)
}
