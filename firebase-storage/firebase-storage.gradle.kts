/*
 * Copyright 2026 Google LLC
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

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("firebase-library")
  id("kotlin-android")
  alias(libs.plugins.protobuf)
  id("copy-google-services")
}

firebaseLibrary {
  libraryGroup = "storage"
  testLab.enabled = true
  publishJavadoc = true
  releaseNotes {
    name.set("{{firebase_storage_full}}")
    versionName.set("storage")
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  installation { timeOutInMs = 60 * 1000 }

  namespace = "com.google.firebase.storage"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {}
    debug {}
  }

  sourceSets {
    getByName("androidTest") {
      java {
        srcDir("src/androidTest/java")
        srcDir("src/testUtil/java")
        // We need this since FirebaseApp rule is externalized
        // TODO(ashwinraghav) b/113079738
        srcDir("../firebase-common/src/testUtil")
      }
    }

    getByName("test") {
      java {
        srcDir("src/testUtil/java")
        // We need this since FirebaseApp rule is externalized
        // TODO(ashwinraghav) b/113079738
        srcDir("../firebase-common/src/testUtil")
      }
      resources { srcDirs("src/test/resources") }
    }
  }

  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }

dependencies {
  javadocClasspath(libs.autovalue.annotations)
  javadocClasspath(libs.findbugs.jsr305)

  api(libs.firebase.annotations)
  api("com.google.firebase:firebase-appcheck:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-common-ktx")
    exclude(group = "com.google.firebase", module = "firebase-components")
    exclude(group = "com.google.firebase", module = "firebase-annotations")
  }
  api("com.google.firebase:firebase-appcheck-interop:18.0.0")
  api("com.google.firebase:firebase-auth-interop:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
    exclude(group = "com.google.firebase", module = "firebase-annotations")
  }
  api(libs.firebase.common)
  api(libs.firebase.components)

  implementation(libs.androidx.annotation)
  implementation(libs.playservices.base)
  implementation(libs.playservices.tasks)
  implementation(libs.kotlin.stdlib)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.rules)
  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.robolectric)

  androidTestImplementation(libs.androidx.annotation)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.junit)
}
