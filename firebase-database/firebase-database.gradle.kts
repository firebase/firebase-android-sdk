@file:Suppress("UnstableApiUsage")

// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

plugins {
  id("firebase-library")
}

firebaseLibrary {
  testLab.enabled = true
  publishSources = true
}

android {
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  installation.timeOutInMs = 60 * 1000

  compileSdk = targetSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release { isMinifyEnabled = false }
  }

  sourceSets {
    named("androidTest") {
      java.srcDirs("src/testUtil/java")
    }
    named("test") {
      java.srcDirs("src/testUtil")
    }
    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions {
      resources.excludes.add("META-INF/DEPENDENCIES")
    }

    testOptions.unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  implementation("com.google.firebase:firebase-common:20.3.1")
  implementation("com.google.firebase:firebase-database-collection:18.0.1")
  implementation("com.google.firebase:firebase-components:17.1.0")
  implementation("com.google.firebase:firebase-appcheck-interop:16.1.1")

  implementation("androidx.annotation:annotation:1.1.0")
  implementation("com.google.android.gms:play-services-basement:18.1.0")
  implementation("com.google.android.gms:play-services-base:18.0.1")
  implementation("com.google.android.gms:play-services-tasks:18.0.1")
  implementation("com.google.firebase:firebase-auth-interop:19.0.2") {
    exclude("com.google.firebase", "firebase-common")
  }

  javadocClasspath("com.google.code.findbugs:jsr305:3.0.2")
  javadocClasspath("com.google.auto.value:auto-value-annotations:1.6.6")

  androidTestImplementation("androidx.annotation:annotation:1.1.0")
  androidTestImplementation("androidx.test:rules:1.2.0")
  androidTestImplementation("androidx.test:runner:1.2.0")
  androidTestImplementation("androidx.test.ext:junit:1.1.1")
  androidTestImplementation(libs.truth)
  androidTestImplementation("com.fasterxml.jackson.core:jackson-core:2.9.8")
  androidTestImplementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
  androidTestImplementation("junit:junit:4.12")
  androidTestImplementation("org.hamcrest:hamcrest:2.2")
  androidTestImplementation("org.hamcrest:hamcrest-library:2.2")
  androidTestImplementation("net.java:quickcheck:0.6")

  testImplementation("junit:junit:4.12")
  testImplementation("org.mockito:mockito-core:2.25.0")
  testImplementation(libs.robolectric)
  testImplementation("com.firebase:firebase-token-generator:2.0.0")
  testImplementation("com.fasterxml.jackson.core:jackson-core:2.9.8")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
  testImplementation("net.java.quickcheck:quickcheck:0.6")
  testImplementation(libs.truth) // does androidTestImpl not derive from testImpl?
  testImplementation("androidx.test:core:1.2.0")
  testImplementation("androidx.test:rules:1.2.0")
}

// ==========================================================================
// Copy from here down if you want to use the google-services plugin in your
// androidTest integration tests.
// ==========================================================================
ext["packageName"] = "com.google.firebase.database"
apply("../gradle/googleServices.gradle")
