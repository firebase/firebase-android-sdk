/*
 * Copyright 2024 Google LLC
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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("firebase-library")
  id("kotlin-android")
  alias(libs.plugins.kotlinx.serialization)
}

firebaseLibrary {
  testLab.enabled = false
  publishJavadoc = true
  releaseNotes {
    name.set("{{firebase_ai}}")
    versionName.set("ai")
    hasKTX.set(false)
  }
}

android {
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.ai"
  compileSdk = 34
  defaultConfig {
    minSdk = 21
    consumerProguardFiles("consumer-rules.pro")
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
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

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinCompile>().all {
  if (!name.contains("test", ignoreCase = true)) {
    if (!kotlinOptions.freeCompilerArgs.contains("-Xexplicit-api=strict")) {
      kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}

dependencies {
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.websockets)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.client.logging)

  api("com.google.firebase:firebase-common:21.0.0")
  implementation("com.google.firebase:firebase-components:18.0.0")
  implementation("com.google.firebase:firebase-annotations:16.2.0")
  implementation("com.google.firebase:firebase-appcheck-interop:17.1.0")
  implementation(libs.androidx.annotation)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.core.ktx)
  implementation(libs.slf4j.nop)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.reactive)
  implementation(libs.reactive.streams)
  implementation("com.google.guava:listenablefuture:1.0")
  implementation("androidx.concurrent:concurrent-futures:1.2.0")
  implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
  implementation("com.google.firebase:firebase-auth-interop:18.0.0")

  testImplementation(libs.kotest.assertions.core)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.assertions.json)
  testImplementation(libs.ktor.client.okhttp)
  testImplementation(libs.ktor.client.mock)
  testImplementation(libs.org.json)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
  testImplementation(libs.mockito.core)

  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}
