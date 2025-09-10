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

import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  id("com.android.library")
  id("kotlin-android")
  alias(libs.plugins.kotlinx.serialization)
  id("com.google.firebase.dataconnect.gradle.plugin")
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect.connectors"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  @Suppress("UnstableApiUsage")
  testOptions {
    targetSdk = targetSdkVersion
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  lint { targetSdk = targetSdkVersion }

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }

  dataconnect {
    configDir = file("../emulator/dataconnect")
    codegen { connectors = listOf("demo", "keywords") }
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_1_8
    optIn.add("kotlin.RequiresOptIn")
  }
}

dependencies {
  implementation(project(":firebase-dataconnect"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)

  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.property)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotest.assertions)
  androidTestImplementation(libs.kotest.property)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.testonly.three.ten.abp)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.truth.liteproto.extension)
  androidTestImplementation(libs.turbine)
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinJvmCompile>().configureEach {
  if (!name.contains("test", ignoreCase = true)) {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
  }
}
