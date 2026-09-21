import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/*
 * Copyright 2026 Google LLC
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
  id("firebase-library")
  id("kotlin-android")
}

firebaseLibrary {
  libraryGroup = "crashlytics"

  testLab.enabled = true
  publishJavadoc = false

  releaseNotes { enabled = false }
}

// Path to the native firebase-telemetry-persistence core library.
// Can be customized via the FIREBASE_PERSISTENCE_DIR environment variable
// or firebase.persistence.dir in local.properties. If not set, it defaults
// to the adjacent sibling directory (../firebase-telemetry-persistence) or
// fetches from https://github.com/firebase/firebase-telemetry-persistence.git via CMake
// FetchContent.
val persistenceDir =
  providers
    .gradleProperty("firebase.persistence.dir")
    .orElse(providers.environmentVariable("FIREBASE_PERSISTENCE_DIR"))
    .orElse(
      rootProject.rootDir.resolve("../firebase-telemetry-persistence").normalize().absolutePath
    )

android {
  val compileSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.crashlytics.telemetry"
  compileSdk = compileSdkVersion

  defaultConfig {
    minSdk = minSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    externalNativeBuild {
      cmake { arguments("-DFIREBASE_PERSISTENCE_DIR=${persistenceDir.get()}") }
    }
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

  externalNativeBuild {
    cmake {
      path = file("src/main/cpp/CMakeLists.txt")
      version = "3.22.1"
    }
  }

  testOptions { unitTests.all { testTask -> testTask.useJUnitPlatform() } }
}

kotlin {
  explicitApi()
  compilerOptions { jvmTarget = JvmTarget.JVM_1_8 }
}

dependencies {
  api(project(":firebase-crashlytics"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.okhttp)

  implementation(platform(libs.opentelemetry.bom))
  implementation(libs.opentelemetry.sdk)
  implementation(libs.opentelemetry.api)
  implementation(libs.opentelemetry.kotlin.extension)
  implementation(libs.opentelemetry.exporter.otlp)
  implementation(libs.opentelemetry.semconv)

  testImplementation(libs.junit.jupiter)
  testImplementation(libs.opentelemetry.sdk.testing)
  testImplementation(libs.truth)

  testRuntimeOnly(libs.junit.jupiter.engine)
  testRuntimeOnly(libs.junit.platform.launcher)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.opentelemetry.sdk.testing)
  androidTestImplementation(libs.androidx.espresso.core)
}
