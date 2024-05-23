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
    libraryGroup("common")
    publishJavadoc = false
    publishReleaseNotes = false
}

android {
    val compileSdkVersion : Int by rootProject
    val targetSdkVersion : Int by rootProject
    val minSdkVersion : Int by rootProject
    compileSdk = compileSdkVersion
    namespace = "com.google.firebase.ktx"
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(project(":firebase-common"))
    implementation("com.google.firebase:firebase-components:18.0.0")
    implementation("com.google.firebase:firebase-annotations:16.2.0")
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
}
