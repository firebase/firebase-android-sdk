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
  publishSources = true
  publishJavadoc = true
  releaseNotes {
    name.set("{{firebase_vertexai}}")
    versionName.set("vertex-ai")
    hasKTX.set(false)
  }
}

android {
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.vertexai"
  compileSdk = 34
  defaultConfig {
    minSdk = 21
    targetSdk = 34
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
    unitTests.isIncludeAndroidResources = true
    unitTests.isReturnDefaultValues = true
  }
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
  val ktorVersion = "2.3.2"

  implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
  implementation("io.ktor:ktor-client-core:$ktorVersion")
  implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
  implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
  implementation("io.ktor:ktor-client-logging:$ktorVersion")

  api("com.google.firebase:firebase-common:21.0.0")
  implementation("com.google.firebase:firebase-components:18.0.0")
  implementation("com.google.firebase:firebase-annotations:16.2.0")
  implementation("com.google.firebase:firebase-appcheck-interop:17.1.0")
  implementation(libs.androidx.annotation)
  implementation(libs.kotlinx.serialization.json)
  implementation("androidx.core:core-ktx:1.12.0")
  implementation("org.slf4j:slf4j-nop:2.0.9")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.7.3")
  implementation("org.reactivestreams:reactive-streams:1.0.3")
  implementation("com.google.guava:listenablefuture:1.0")
  implementation("androidx.concurrent:concurrent-futures:1.2.0-alpha03")
  implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0-alpha03")
  implementation("com.google.firebase:firebase-auth-interop:18.0.0")

  testImplementation("io.kotest:kotest-assertions-core:5.5.5")
  testImplementation("io.kotest:kotest-assertions-core-jvm:5.5.5")
  testImplementation("io.kotest:kotest-assertions-json:5.5.5")
  testImplementation("io.ktor:ktor-client-okhttp:$ktorVersion")
  testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
  testImplementation("org.json:json:20240303")
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.junit)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)

  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}
