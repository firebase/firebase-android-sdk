import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Copyright 2022 Google LLC
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
  id("firebase-vendor")
  id("copy-google-services")
  kotlin("kapt")
}

firebaseLibrary {
  libraryGroup = "functions"
  testLab.enabled = true
  releaseNotes {
    name.set("{{functions_client}}")
    versionName.set("functions-client")
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.functions"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("proguard.txt")
  }
  sourceSets { getByName("androidTest").java.srcDirs("src/testUtil") }
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
  javadocClasspath("org.codehaus.mojo:animal-sniffer-annotations:1.21")
  javadocClasspath(libs.autovalue.annotations)
  javadocClasspath(libs.findbugs.jsr305)

  api("com.google.firebase:firebase-appcheck-interop:17.1.0")
  api("com.google.firebase:firebase-common:22.0.0")
  api("com.google.firebase:firebase-components:19.0.0")
  api("com.google.firebase:firebase-annotations:17.0.0")
  api("com.google.firebase:firebase-auth-interop:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
  }
  api("com.google.firebase:firebase-iid:21.1.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  api("com.google.firebase:firebase-iid-interop:17.1.0")
  api(libs.reactive.streams)
  api(libs.playservices.tasks)

  implementation(libs.kotlin.stdlib)
  implementation(libs.playservices.base)
  implementation(libs.playservices.basement)
  implementation(libs.playservices.tasks)
  implementation(libs.okhttp)
  implementation(libs.androidx.annotation)
  implementation(libs.javax.inject)
  implementation(libs.kotlin.stdlib)
  implementation(libs.okhttp)
  implementation(libs.playservices.base)
  implementation(libs.playservices.basement)

  kapt(libs.autovalue)
  annotationProcessor(libs.dagger.compiler)

  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.rules)
  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.robolectric)
  testImplementation(libs.robolectric) {}
  testImplementation(libs.truth)
  vendor(libs.dagger.dagger) { exclude(group = "javax.inject", module = "javax.inject") }

  androidTestImplementation(project(":integ-testing"))
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.kotlinx.coroutines.reactive)
  androidTestImplementation(libs.mockito.core)
  androidTestImplementation(libs.mockito.dexmaker)
  kapt("com.google.dagger:dagger-android-processor:2.43.2")
  kapt(libs.dagger.compiler)
}
