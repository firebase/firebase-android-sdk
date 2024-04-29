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
    id("kotlin-android")
    id("copy-google-services")
}

firebaseLibrary {
  libraryGroup("database")
  testLab.enabled = true
  publishSources = true
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  installation.timeOutInMs = 60 * 1000
  compileSdk = compileSdkVersion

  namespace = "com.google.firebase.database"
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes { release { isMinifyEnabled = false } }

  sourceSets {
    named("androidTest") { java.srcDir("src/testUtil/java") }
    named("test") { java.srcDir("src/testUtil") }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions { jvmTarget = "1.8" }

    packagingOptions.resources.excludes += "META-INF/DEPENDENCIES"
    testOptions.unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
    api("com.google.firebase:firebase-appcheck-interop:17.1.0")
    api("com.google.firebase:firebase-common:21.0.0")
    api("com.google.firebase:firebase-common-ktx:21.0.0")
    api("com.google.firebase:firebase-components:18.0.0")
    api("com.google.firebase:firebase-auth-interop:20.0.0") {
     exclude(group = "com.google.firebase", module = "firebase-common")
     exclude(group = "com.google.firebase", module = "firebase-components")
   }
    api("com.google.firebase:firebase-database-collection:18.0.1")
    implementation(libs.androidx.annotation)
    implementation(libs.bundles.playservices)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.core)
    api(libs.playservices.tasks)

    testImplementation("com.fasterxml.jackson.core:jackson-core:2.13.1")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    testImplementation("com.firebase:firebase-token-generator:2.0.0")
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.quickcheck)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)

    androidTestImplementation("com.fasterxml.jackson.core:jackson-core:2.13.1")
    androidTestImplementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
    androidTestImplementation("org.hamcrest:hamcrest:2.2")
    androidTestImplementation("org.hamcrest:hamcrest-library:2.2")
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.quickcheck)
    androidTestImplementation(libs.truth)
}
