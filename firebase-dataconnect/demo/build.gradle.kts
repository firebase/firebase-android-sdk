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

plugins {
  id("com.android.application") version "8.11.0"
  id("com.google.gms.google-services") version "4.4.2"
  val kotlinVersion = "2.1.10"
  kotlin("android") version kotlinVersion
  kotlin("plugin.serialization") version kotlinVersion
  id("com.diffplug.spotless") version "7.0.0.BETA4"
  id("org.jetbrains.dokka") version "2.0.0"
}

dependencies {
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("androidx.activity:activity-ktx:1.10.1")
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("com.google.protobuf:protobuf-javalite:3.25.5")

  // The following code in this "dependencies" block can be omitted from customer
  // facing documentation as it is an implementation detail of this application.
  implementation("io.kotest:kotest-property:5.9.1")
  implementation("io.kotest.extensions:kotest-property-arbs:2.1.2")
}

dokka {
  moduleName.set("Data Connect Demo")
  dokkaSourceSets.main {
    sourceRoots.from(layout.buildDirectory.dir("dataConnect/generatedSources/").get())
  }
}

// The remaining code in this file can be omitted from customer facing
// documentation. It's here just to make things compile and/or configure
// optional components of the build (e.g. spotless code formatting).

android {
  namespace = "com.google.firebase.dataconnect.minimaldemo"
  compileSdk = 36
  defaultConfig {
    minSdk = 36
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    viewBinding = true
    buildConfig = true
  }
  kotlinOptions.jvmTarget = "17"
  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
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
    indentWithSpaces(2)
    endWithNewline()
  }
}
