// Copyright 2024 Google LLC
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
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
}

android {
  namespace = "com.google.firebase.dataconnect.demo"
  compileSdk = 34

  defaultConfig {
    minSdk = 33
    targetSdk = 33
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions {
    jvmTarget = "1.8"
  }
  buildFeatures {
    compose = true
    viewBinding = true
  }
  composeOptions {
    // Chosen based on the mapping to Kotlin 1.8.22 here:
    // https://developer.android.com/jetpack/androidx/releases/compose-kotlin
    kotlinCompilerExtensionVersion = "1.4.8"
  }
}

dependencies {
  implementation(project(":firebase-dataconnect"))

  implementation("androidx.core:core-ktx:1.12.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
  implementation("androidx.activity:activity-compose:1.8.2")
  implementation(platform("androidx.compose:compose-bom:2024.01.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.material3:material3")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-tooling-preview")

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}

// This is a workaround for gradle build error like this:
// Task ':firebase-dataconnect:connectors:extractDeepLinksDebug' uses this output of task
// ':firebase-dataconnect:copyRootGoogleServices' without declaring an explicit or
// implicit dependency. 
afterEvaluate {
  tasks.named<Task>("extractDeepLinksDebug") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("packageDebugResources") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("processDebugManifest") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeDebugAndroidTestResources") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeDebugShaders") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeDebugAndroidTestShaders") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeDebugJniLibFolders") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeExtDexDebugAndroidTest") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeDebugAndroidTestJniLibFolders") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
  tasks.named<Task>("mergeExtDexDebug") {
    dependsOn(":firebase-dataconnect:copyRootGoogleServices")
  }
}
