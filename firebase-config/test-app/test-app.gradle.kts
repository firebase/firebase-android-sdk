@file:Suppress("DEPRECATION") // App projects should still use FirebaseTestLabPlugin.

import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabPlugin

/*
 * Copyright 2023 Google LLC
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
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("com.google.gms.google-services")
  id("com.google.firebase.crashlytics")
  id("com.google.firebase.firebase-perf")
  id("copy-google-services")
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion : Int by rootProject

  namespace = "com.google.firebase.testing.config"
  compileSdk = compileSdkVersion
  defaultConfig {
    applicationId = "com.google.firebase.testing.config"
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    versionCode = 1
    versionName = "1.0"
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

dependencies {
  implementation(project(":firebase-crashlytics")) {
    exclude(group = "com.google.firebase", module = "firebase-config-interop")
  }
  implementation(project(":firebase-config")) {
    exclude(group = "com.google.firebase", module = "firebase-config-interop")
  }
  implementation(project(":firebase-config:ktx"))

  // This is required since a `project` dependency on frc does not expose the APIs of its
  // "implementation" dependencies. The alternative would be to make common an "api" dep of remote-config.
  // Released artifacts don't need these dependencies since they don't use `project` to refer
  // to Remote Config.
  implementation("com.google.firebase:firebase-common:21.0.0")
  implementation("com.google.firebase:firebase-common-ktx:21.0.0")
  implementation("com.google.firebase:firebase-components:18.0.0")

  implementation("com.google.firebase:firebase-installations-interop:17.1.0")
  runtimeOnly("com.google.firebase:firebase-installations:17.1.4")

  implementation("com.google.android.gms:play-services-basement:18.1.0")
  implementation("com.google.android.gms:play-services-tasks:18.0.1")
  // End RC `project` transitive dependencies

  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.core:core-ktx:1.9.0")
  implementation("com.google.android.material:material:1.8.0")

  androidTestImplementation("com.google.firebase:firebase-common-ktx:21.0.0")
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}

apply<FirebaseTestLabPlugin>()
