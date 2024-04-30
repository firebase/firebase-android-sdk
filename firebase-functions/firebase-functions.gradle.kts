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
    id("firebase-vendor")
    id("copy-google-services")
}

firebaseLibrary {
    libraryGroup("functions")
    testLab.enabled = true
    publishSources = true
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  namespace = "com.google.firebase.functions"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("proguard.txt")
  }
  sourceSets {
    getByName("androidTest").java.srcDirs("src/testUtil")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    javadocClasspath("org.codehaus.mojo:animal-sniffer-annotations:1.21")
    javadocClasspath(libs.autovalue.annotations)
    javadocClasspath(libs.findbugs.jsr305)

    api("com.google.firebase:firebase-appcheck-interop:17.1.0")
    api("com.google.firebase:firebase-common:21.0.0")
    api("com.google.firebase:firebase-common-ktx:21.0.0")
    api("com.google.firebase:firebase-components:18.0.0")
    api("com.google.firebase:firebase-annotations:16.2.0")
    api("com.google.firebase:firebase-auth-interop:18.0.0") {
       exclude(group = "com.google.firebase", module = "firebase-common")
   }
    api("com.google.firebase:firebase-iid:21.1.0") {
       exclude(group = "com.google.firebase", module = "firebase-common")
       exclude(group = "com.google.firebase", module = "firebase-components")
   }
    api("com.google.firebase:firebase-iid-interop:17.1.0")
    implementation(libs.androidx.annotation)
    implementation(libs.javax.inject)
    implementation(libs.kotlin.stdlib)
    implementation(libs.okhttp)
    implementation(libs.playservices.base)
    implementation(libs.playservices.basement)
    api(libs.playservices.tasks)

    annotationProcessor(libs.autovalue)
    annotationProcessor(libs.dagger.compiler)

    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric) {}
    testImplementation(libs.truth)
    vendor(libs.dagger.dagger) {
     exclude(group = "javax.inject", module = "javax.inject")
   }

    androidTestImplementation(project(":integ-testing"))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.mockito.dexmaker)
    androidTestImplementation(libs.truth)
}
