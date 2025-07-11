/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
  id("firebase-library")
  id("kotlin-android")
}

firebaseLibrary {
  libraryGroup = "config"
  testLab.enabled = true
  releaseNotes {
    name.set("{{remote_config}}")
    versionName.set("remote-config")
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.remoteconfig"
  compileSdk = targetSdkVersion

  defaultConfig {
    minSdk = rootProject.extra["minSdkVersion"] as Int
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  sourceSets { getByName("androidTest").java.srcDir("src/androidTest/res") }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  kotlinOptions { jvmTarget = "1.8" }

  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }
  lint { targetSdk = targetSdkVersion }
}

dependencies {
  // Firebase
  api("com.google.firebase:firebase-config-interop:16.0.1")
  api("com.google.firebase:firebase-annotations:17.0.0")
  api("com.google.firebase:firebase-installations-interop:17.1.0")
  api("com.google.firebase:firebase-abt:21.1.1") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  api("com.google.firebase:firebase-measurement-connector:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  api("com.google.firebase:firebase-common:22.0.0")
  api("com.google.firebase:firebase-components:19.0.0")
  api("com.google.firebase:firebase-installations:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common-ktx")
  }

  // Kotlin & Android
  implementation(libs.kotlin.stdlib)
  implementation(libs.androidx.annotation)
  api(libs.playservices.tasks)

  // Annotations and static analysis
  annotationProcessor(libs.autovalue)
  javadocClasspath(libs.autovalue.annotations)
  compileOnly(libs.autovalue.annotations)
  compileOnly(libs.errorprone.annotations)
  compileOnly(libs.findbugs.jsr305)

  // Testing
  testImplementation(libs.junit)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.androidx.test.truth)
  testImplementation(libs.robolectric)
  testImplementation(libs.mockito.core)
  testImplementation(libs.truth)
  testImplementation(libs.jsonassert)

  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.mockito.core)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.jsonassert)
  androidTestImplementation(libs.mockito.dexmaker)
  androidTestImplementation(libs.dexmaker)
}
