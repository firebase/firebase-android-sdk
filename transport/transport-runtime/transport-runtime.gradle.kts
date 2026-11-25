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

plugins {
  alias(libs.plugins.protobuf)
  id("firebase-library")
  id("firebase-vendor")
  id("kotlin-android")
}

// add a dependency on the protoc plugin's fat jar to make it available to protobuf below.
configurations.create("protobuild")

dependencies {
  "protobuild"(project(":encoders:protoc-gen-firebase-encoders", configuration = "shadow"))
}

protobuf {
  protoc { artifact = "${libs.protoc.get()}" }
  plugins {
    register("firebaseEncoders") {
      path =
        "${project(":encoders:protoc-gen-firebase-encoders").layout.buildDirectory.get().asFile.path}/libs/protoc-gen-firebase-encoders-all.jar"
    }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.dependsOn(configurations.named("protobuild"))
      task.inputs.file("code-gen-cfg.textproto")
      task.plugins { register("firebaseEncoders") { option(file("code-gen-cfg.textproto").path) } }
      task.builtins { findByName("java")?.let { remove(it) } }
    }
  }
}

firebaseLibrary {
  libraryGroup = "transport"
  publishJavadoc = false
  releaseNotes { enabled.set(false) }
  testLab {
    enabled = true

    device("model=panther,version=33") // Pixel7
    device("model=redfin,version=30") // Pixel5
    device("model=x1q,version=29")
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.android.datatransport.runtime"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  testOptions {
    targetSdk = targetSdkVersion
    unitTests { isIncludeAndroidResources = true }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  sourceSets {
    named("main") { proto { srcDir("src/main/proto") } }
    named("androidTest") { aidl { srcDir("src/androidTest/aidl") } }
  }
  buildFeatures { aidl = true }
}

thirdPartyLicenses {
  add("Apache-2.0", "${rootDir}/third_party/licenses/apache-2.0.txt")
  add("Dagger", "${rootDir}/third_party/licenses/dagger.txt")
}

dependencies {
  vendor(libs.dagger.dagger) { exclude(group = "javax.inject", module = "javax.inject") }

  api("com.google.firebase:firebase-encoders:18.0.0")
  api("com.google.firebase:firebase-encoders-proto:17.0.0")
  api("com.google.android.datatransport:transport-api:5.0.0")

  implementation("androidx.annotation:annotation:1.3.0")
  implementation(libs.javax.inject)

  compileOnly(libs.autovalue.annotations)
  compileOnly(libs.errorprone.annotations)

  annotationProcessor(project(":encoders:firebase-encoders-processor"))
  annotationProcessor("com.google.auto.value:auto-value:1.6.5")
  annotationProcessor(libs.dagger.compiler)

  androidTestAnnotationProcessor(libs.dagger.compiler)

  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.truth)
  testImplementation("junit:junit:4.13-beta-2")
  testImplementation("org.mockito:mockito-core:2.25.0")
  testImplementation(libs.robolectric)

  androidTestImplementation("androidx.test:rules:1.2.0")
  androidTestImplementation("androidx.test:runner:1.2.0")
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation("junit:junit:4.13-beta-3")
  androidTestImplementation("org.mockito:mockito-android:2.25.0")
  androidTestImplementation("org.mockito:mockito-core:2.25.0")
}
