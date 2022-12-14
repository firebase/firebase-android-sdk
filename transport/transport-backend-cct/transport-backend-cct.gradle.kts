import com.google.protobuf.gradle.protoc

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
  id("com.google.protobuf")
}

firebaseLibrary{
  publishJavadoc = false
}

protobuf {
    // Configure the protoc executable
    protobuf.protoc {
        val protocVersion = libs.versions.protoc
        artifact = "com.google.protobuf:protoc:$protocVersion"
    }
}

android {
    val targetSdkVersion : Int by rootProject
    val minSdkVersion : Int by rootProject
    compileSdk = targetSdkVersion
    defaultConfig {
        minSdk = minSdkVersion
        targetSdk = targetSdkVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("proguard.txt")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":transport:transport-api"))
    implementation(project(":transport:transport-runtime"))
    implementation(project(":encoders:firebase-encoders"))
    implementation(project(":encoders:firebase-encoders-json"))
    implementation(libs.androidx.annotation)

    compileOnly(libs.autovalue.annotations)

    annotationProcessor(libs.autovalue)
    annotationProcessor(project(":encoders:firebase-encoders-processor"))

    testImplementation("com.google.protobuf:protobuf-java-util:3.11.0")
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.truth)
    testImplementation("com.google.truth.extensions:truth-proto-extension:1.0")
    testImplementation("com.github.tomakehurst:wiremock:2.26.3")
    //Android compatible version of Apache httpclient.
    testImplementation("org.apache.httpcomponents:httpclient-android:4.3.5.1")
    // Keep Robolectric to 4.3.1 for httpclient and TelephonyManager compatibility.
    testImplementation("org.robolectric:robolectric:4.3.1")
    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso)
}
