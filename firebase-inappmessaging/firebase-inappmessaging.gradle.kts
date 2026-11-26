/*
 * Copyright 2026 Google LLC
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

import com.google.protobuf.gradle.proto
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("firebase-library")
  id("kotlin-android")
  alias(libs.plugins.protobuf)
  id("firebase-vendor")
}

firebaseLibrary {
  libraryGroup = "inappmessaging"
  testLab.enabled = true
  releaseNotes {
    name.set("{{inappmessaging}}")
    versionName.set("inappmessaging")
  }
}

protobuf {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "${libs.protoc.get()}"
  }
  plugins { register("grpc") { artifact = "${libs.grpc.protoc.gen.java.get()}" } }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins { maybeCreate("java").apply { option("lite") } }
      task.plugins { register("grpc") { option("lite") } }
    }
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  installation { timeOutInMs = 60 * 1000 }

  namespace = "com.google.firebase.inappmessaging"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("proguard.txt")
  }
  sourceSets {
    named("main") { proto { srcDir("src/proto") } }
    named("androidTest") {
      java {
        srcDir("src/testUtil")
        srcDir("src/testData")
      }
    }
    named("test") {
      java {
        srcDir("src/testUtil")
        srcDir("src/testData")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_1_8 } }

thirdPartyLicenses {
  add("Apache-2.0", "${rootDir}/third_party/licenses/apache-2.0.txt")
  add("Dagger", "${rootDir}/third_party/licenses/dagger.txt")
}

dependencies {
  vendor(libs.dagger.dagger) { exclude(group = "javax.inject", module = "javax.inject") }

  api(project(":protolite-well-known-types"))
  api(libs.playservices.tasks)
  api("com.google.firebase:firebase-abt:24.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  api(libs.firebase.common)
  api(libs.firebase.components)
  api("com.google.firebase:firebase-datatransport:21.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  api("com.google.firebase:firebase-installations:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common-ktx")
  }
  api("com.google.firebase:firebase-installations-interop:18.0.0")
  api("com.google.firebase:firebase-measurement-connector:18.0.2") {
    exclude(group = "com.google.firebase", module = "firebase-common")
  }

  implementation(libs.androidx.annotation)
  implementation("com.google.android.datatransport:transport-api:5.0.0")
  implementation(libs.autovalue.annotations)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.stub)
  implementation(libs.rxandroid)
  implementation(libs.rxjava)
  implementation(libs.javax.inject)
  implementation(libs.kotlin.stdlib)

  compileOnly(libs.javax.annotation.jsr250)

  annotationProcessor(libs.autovalue)
  annotationProcessor(libs.auto.value.parcel)
  annotationProcessor(libs.dagger.compiler)

  androidTestAnnotationProcessor(libs.dagger.compiler)

  testImplementation(project(":integ-testing")) {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.androidx.test.junit)
  testImplementation("com.google.guava:guava:30.1-android")
  testImplementation(libs.truth)
  testImplementation(libs.grpc.testing)
  testImplementation(libs.junit)
  testImplementation(libs.mockito.core)
  testImplementation(libs.robolectric) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }

  androidTestImplementation(project(":integ-testing")) {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  androidTestImplementation(libs.androidx.annotation)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.dexmaker)
  androidTestImplementation(libs.mockito.dexmaker)
  androidTestImplementation(libs.grpc.testing)
  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.awaitility)
}
