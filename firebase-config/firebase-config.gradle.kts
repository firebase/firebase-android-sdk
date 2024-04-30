/*
 * Copyright 2018 Google LLC
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
    id("kotlin-android")
}

firebaseLibrary {
    libraryGroup("config")
    testLab.enabled = true
    publishSources = true

}

android {
    val compileSdkVersion : Int by rootProject
    val targetSdkVersion: Int by rootProject
    val minSdkVersion : Int by rootProject

    namespace = "com.google.firebase.remoteconfig"
    compileSdk = targetSdkVersion

    defaultConfig {
      minSdk = 21
      targetSdk = targetSdkVersion
      multiDexEnabled = true
      testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("androidTest").java.srcDir("src/androidTest/res")
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
    // Firebase
    api("com.google.firebase:firebase-config-interop:16.0.1")
    api("com.google.firebase:firebase-annotations:16.2.0")
    api("com.google.firebase:firebase-installations-interop:17.1.0")
    api("com.google.firebase:firebase-abt:21.1.1") {
         exclude(group = "com.google.firebase", module = "firebase-common")
         exclude(group = "com.google.firebase", module = "firebase-components")
     }
    api("com.google.firebase:firebase-measurement-connector:18.0.0") {
         exclude(group = "com.google.firebase", module = "firebase-common")
         exclude(group = "com.google.firebase", module = "firebase-components")
     }
    api("com.google.firebase:firebase-common:21.0.0")
    api("com.google.firebase:firebase-common-ktx:21.0.0")
    api("com.google.firebase:firebase-components:18.0.0")
    api("com.google.firebase:firebase-installations:17.2.0")

    // Kotlin & Android
    implementation(libs.kotlin.stdlib)
    implementation("androidx.annotation:annotation:1.1.0")
    api("com.google.android.gms:play-services-tasks:18.0.1")

    // Annotations and static analysis
    annotationProcessor("com.google.auto.value:auto-value:1.6.6")
    javadocClasspath("com.google.auto.value:auto-value-annotations:1.6.6")
    compileOnly("com.google.auto.value:auto-value-annotations:1.6.6")
    compileOnly("com.google.code.findbugs:jsr305:3.0.2")

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.truth)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    testImplementation(libs.truth)
    testImplementation("org.skyscreamer:jsonassert:1.5.0")

    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.mockito.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation("org.skyscreamer:jsonassert:1.5.0")
    androidTestImplementation("com.linkedin.dexmaker:dexmaker-mockito:2.28.1")
    androidTestImplementation("com.linkedin.dexmaker:dexmaker:2.28.1")
}
