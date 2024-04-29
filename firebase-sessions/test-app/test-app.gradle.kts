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

@file:Suppress("DEPRECATION") // App projects should still use FirebaseTestLabPlugin.

import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabExtension
import com.google.firebase.gradle.plugins.ci.device.FirebaseTestLabPlugin

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

  namespace = "com.google.firebase.testing.sessions"
  compileSdk = compileSdkVersion
  buildFeatures.buildConfig = true
  defaultConfig {
    applicationId = "com.google.firebase.testing.sessions"
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    versionCode = 1
    versionName = "1.0"
    multiDexEnabled = true
    multiDexKeepProguard = file("multidex-config.pro")
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // We only want to actually crash the app on specific runs.
    buildConfigField(
      "boolean",
      "SHOULD_CRASH_APP",
      project.hasProperty("triggerCrashes").toString()
    )
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  buildFeatures { viewBinding = true }
}

dependencies {
  if (project.hasProperty("useReleasedVersions")) {
    implementation(platform("com.google.firebase:firebase-bom:latest.release"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-perf")
    implementation("com.google.firebase:firebase-sessions")
  } else {
    implementation(project(":firebase-crashlytics")) {
      exclude(group = "com.google.firebase", module = "firebase-sessions")
    }
    implementation(project(":firebase-perf")) {
      exclude(group = "com.google.firebase", module = "firebase-sessions")
    }
    implementation(project(":firebase-sessions"))
  }

  implementation("androidx.appcompat:appcompat:1.6.1")
  implementation("androidx.constraintlayout:constraintlayout:2.1.4")
  implementation("androidx.core:core-ktx:1.7.0")
  implementation("androidx.multidex:multidex:2.0.1")
  implementation("androidx.navigation:navigation-fragment-ktx:2.4.1")
  implementation("androidx.navigation:navigation-ui-ktx:2.4.1")
  implementation("com.google.android.material:material:1.9.0")
  implementation(libs.androidx.core)

  androidTestImplementation("com.google.firebase:firebase-common:21.0.0")
  androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
}

apply<FirebaseTestLabPlugin>()

configure<FirebaseTestLabExtension> {
  device("model=panther,version=33") // Pixel7
}
