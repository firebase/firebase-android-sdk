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

plugins { id("firebase-library") }

firebaseLibrary {
  libraryGroup("database")
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

  buildTypes { release { isMinifyEnabled = false } }

  sourceSets {
    named("androidTest") { java.srcDir("src/testUtil/java") }
    named("test") { java.srcDir("src/testUtil") }

    compileOptions {
      sourceCompatibility = JavaVersion.VERSION_1_8
      targetCompatibility = JavaVersion.VERSION_1_8
    }

    packagingOptions.resources.excludes += "META-INF/DEPENDENCIES"
    testOptions.unitTests.isIncludeAndroidResources = true
  }
}

dependencies {
  implementation("com.google.firebase:firebase-common:20.3.2")
  implementation("com.google.firebase:firebase-components:17.1.0")
  implementation("com.google.firebase:firebase-auth-interop:20.0.0")
  implementation(project(":appcheck:firebase-appcheck-interop"))
  implementation("com.google.firebase:firebase-database-collection:18.0.1")

  implementation(libs.androidx.annotation)
  implementation(libs.bundles.playservices)

  testImplementation(libs.truth)
  testImplementation(libs.junit)
  testImplementation(libs.quickcheck)
  testImplementation(libs.robolectric)
  testImplementation(libs.mockito.core)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.rules)
  testImplementation("com.firebase:firebase-token-generator:2.0.0")
  testImplementation("com.fasterxml.jackson.core:jackson-core:2.13.1")
  testImplementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")

  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.quickcheck)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation("org.hamcrest:hamcrest:2.2")
  androidTestImplementation("org.hamcrest:hamcrest-library:2.2")
  androidTestImplementation("com.fasterxml.jackson.core:jackson-core:2.13.1")
  androidTestImplementation("com.fasterxml.jackson.core:jackson-databind:2.13.1")
}

ext["packageName"] = "com.google.firebase.database"

apply("../gradle/googleServices.gradle")
