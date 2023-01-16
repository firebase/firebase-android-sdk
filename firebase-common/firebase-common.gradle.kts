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
}

firebaseLibrary {
    testLab.enabled = true
    publishSources = true
}

android {
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  compileSdk = targetSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("proguard.txt")
  }
  sourceSets {
    getByName("androidTest") {
      java.srcDirs("src/testUtil")
    }
    getByName("test") {
      java.srcDirs("src/testUtil")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(project(":firebase-annotations"))
    implementation(project(":firebase-components"))
    implementation(libs.androidx.futures)
    implementation(libs.playservices.basement)
    implementation(libs.playservices.tasks)

    annotationProcessor(libs.autovalue)

    compileOnly(libs.autovalue.annotations)
    compileOnly(libs.findbugs.jsr305)
    // needed for Kotlin detection to compile, but not necessarily present at runtime.
    compileOnly(libs.kotlin.stdlib)

    // FirebaseApp references storage, so storage needs to be on classpath when dokka runs.
    javadocClasspath(project(":firebase-storage"))

    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)

    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.mockito.dexmaker)
    androidTestImplementation(libs.truth)
    androidTestImplementation(project(":integ-testing"))
}
