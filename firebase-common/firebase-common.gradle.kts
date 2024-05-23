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
    testLab.enabled = true
    publishSources = true
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  compileSdk = compileSdkVersion
  namespace = "com.google.firebase"
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
    kotlinOptions {
        jvmTarget = "1.8"
    }
  testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    api(libs.kotlin.coroutines.tasks)

    api("com.google.firebase:firebase-components:18.0.0")
    api("com.google.firebase:firebase-annotations:16.2.0")
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.futures)
    implementation(libs.kotlin.stdlib)
    implementation(libs.playservices.basement)
    implementation(libs.playservices.tasks)

    compileOnly(libs.autovalue.annotations)
    compileOnly(libs.findbugs.jsr305)
    compileOnly(libs.kotlin.stdlib)

    annotationProcessor(libs.autovalue)

    testImplementation("com.google.guava:guava-testlib:12.0-rc2")
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)

    androidTestImplementation(project(":integ-testing")) {
        exclude("com.google.firebase","firebase-common")
        exclude("com.google.firebase","firebase-common-ktx")
    }

    // TODO(Remove when FirbaseAppTest has been modernized to use LiveData)
    androidTestImplementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.mockito.dexmaker)
    androidTestImplementation(libs.truth)
}
