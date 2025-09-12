/*
 * Copyright 2025 Google LLC
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
  id("com.android.test")
  id("org.jetbrains.kotlin.android")
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.benchmark.sessions"
  compileSdk = compileSdkVersion

  defaultConfig {
    targetSdk = targetSdkVersion
    minSdk = 23

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    create("benchmark") {
      isDebuggable = true
      signingConfig = signingConfigs["debug"]
      matchingFallbacks += "release"
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  targetProjectPath = ":firebase-sessions:test-app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }

dependencies {
  implementation(libs.androidx.test.junit)
  implementation(libs.androidx.benchmark.macro)
}

androidComponents {
  beforeVariants(selector().all()) { variantBuilder ->
    variantBuilder.enable = (variantBuilder.buildType == "benchmark")
  }
}
