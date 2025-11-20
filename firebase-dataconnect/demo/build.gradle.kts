/*
 * Copyright 2024 Google LLC
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
  // Use whichever versions of these dependencies suit your application.
  // The versions shown here were the latest versions as of Nov 19, 2025.
  // Note, however, that the version of kotlin("plugin.serialization") _must_,
  // in general, match the version of kotlin("android").
  id("com.android.application") version "8.13.1"
  id("com.google.gms.google-services") version "4.4.4"
  val kotlinVersion = "2.1.21"
  kotlin("android") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion

  id("org.jetbrains.kotlin.plugin.compose") version kotlinVersion

  // The following code in this "plugins" block can be omitted from customer
  // facing documentation as it is an implementation detail of this application.
  id("com.diffplug.spotless") version "8.1.0"
}

dependencies {
  // Use whichever versions of these dependencies suit your application.
  // The versions shown here were the latest versions as of Nov 19, 2025.

  // Data Connect
  implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
  implementation("com.google.firebase:firebase-dataconnect")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("com.google.android.material:material:1.13.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.activity:activity-ktx:1.12.0")
  implementation("androidx.activity:activity-compose:1.12.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
  implementation("com.google.android.material:material:1.13.0")

  // Jetpack compose dependencies.
  implementation(platform("androidx.compose:compose-bom:2025.11.01"))
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.material3:material3")
  debugImplementation("androidx.compose.ui:ui-tooling")

  // The following code in this "dependencies" block can be omitted from customer
  // facing documentation as it is an implementation detail of this application.
  coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
  implementation("io.kotest:kotest-property:5.9.1")
  implementation("io.kotest.extensions:kotest-property-arbs:2.1.2")
}

// The remaining code in this file can be omitted from customer facing
// documentation. It's here just to make things compile and/or configure
// optional components of the build (for example, spotless code formatting).

android {
  namespace = "com.google.firebase.dataconnect.minimaldemo"
  compileSdk = 36
  defaultConfig {
    minSdk = 23
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    isCoreLibraryDesugaringEnabled = true
  }
  buildFeatures.viewBinding = true
  kotlinOptions.jvmTarget = "1.8"
  buildFeatures.compose = true
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_1_8
    optIn.add("kotlin.RequiresOptIn")
  }
}

spotless {
  val ktfmtVersion = "0.53"
  kotlin {
    target("**/*.kt")
    targetExclude("build/")
    ktfmt(ktfmtVersion).googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("build/")
    ktfmt(ktfmtVersion).googleStyle()
  }
  json {
    target("**/*.json")
    targetExclude("build/")
    simple().indentWithSpaces(2)
  }
  yaml {
    target("**/*.yaml")
    targetExclude("build/")
    jackson()
      .yamlFeature("INDENT_ARRAYS", true)
      .yamlFeature("MINIMIZE_QUOTES", true)
      .yamlFeature("WRITE_DOC_START_MARKER", false)
  }
  format("xml") {
    target("**/*.xml")
    targetExclude("build/")
    trimTrailingWhitespace()
    leadingTabsToSpaces(2)
    endWithNewline()
  }
}
