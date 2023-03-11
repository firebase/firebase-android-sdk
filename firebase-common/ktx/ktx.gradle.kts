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
}

firebaseLibrary {
    releaseWith(project(":firebase-common"))
}

android {
    val targetSdkVersion : Int by rootProject
    val minSdkVersion : Int by rootProject
    compileSdk = targetSdkVersion
    defaultConfig {
        minSdk = minSdkVersion
        targetSdk = targetSdkVersion
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation("com.google.firebase:firebase-annotations:16.2.0")
    implementation(project(":firebase-common"))
    implementation("com.google.firebase:firebase-components:17.1.0")
    implementation(libs.androidx.annotation)

    // We"re exposing this library as a transitive dependency so developers can
    // get Kotlin Coroutines support out-of-the-box for methods that return a Task
    api(libs.kotlin.coroutines.tasks)

    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlin.coroutines.test)
}
