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
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("maven-publish")
}

// Path to the native firebase-telemetry-persistence core library.
// Can be customized via the FIREBASE_PERSISTENCE_DIR environment variable
// or firebase.persistence.dir in gradle.properties. If not set, it defaults
// to the adjacent sibling directory (../firebase-telemetry-persistence) or
// fetches from https://github.com/firebase/firebase-telemetry-persistence.git via CMake FetchContent.
val persistenceDir = providers.gradleProperty("firebase.persistence.dir")
  .orElse(providers.environmentVariable("FIREBASE_PERSISTENCE_DIR"))
  .orElse(rootProject.rootDir.resolve("../firebase-telemetry-persistence").normalize().absolutePath)

android {
  val compileSdkVersion: Int by rootProject

  namespace = "com.google.firebase.crashlytics.telemetry"
  compileSdk = compileSdkVersion

  defaultConfig {
    minSdk = 23
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    externalNativeBuild {
      cmake {
        arguments("-DFIREBASE_PERSISTENCE_DIR=${persistenceDir.get()}")
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }

  publishing {
    singleVariant("release") {
      withSourcesJar()
    }
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
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
  }
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

publishing {
  publications {
    register<MavenPublication>("release") {
      groupId = "com.google.firebase"
      artifactId = "firebase-crashlytics-telemetry"
      version = "0.1.0-SNAPSHOT"

      afterEvaluate {
        from(components["release"])
      }
    }
  }
}
