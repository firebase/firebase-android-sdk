/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("firebase-library")
}

firebaseLibrary {
    publishSources = true
    publishJavadoc = false
    publishReleaseNotes = false
}

android {
    val targetSdkVersion: Int by rootProject

    namespace = "com.google.firebase.remoteconfiginterop"
    compileSdk = 33

    defaultConfig {
        minSdk = 16
        targetSdk = targetSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    api("com.google.firebase:firebase-encoders-json:18.0.1")
    api("com.google.firebase:firebase-encoders:17.0.0")

    compileOnly("com.google.auto.value:auto-value-annotations:1.10.1")

    annotationProcessor("com.google.auto.value:auto-value:1.10.1")
    annotationProcessor(project(":encoders:firebase-encoders-processor"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.robolectric)

    androidTestImplementation(libs.androidx.test.junit)
}
