import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Copyright 2024 Google LLC
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
  id("com.google.protobuf")
}

android {
  val targetSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect.androidTestutil"
  compileSdk = 33
  defaultConfig {
    minSdk = 21
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }
}

protobuf {
  protoc {
    artifact = "${libs.protoc.get()}"
  }
  plugins {
    create("java") {
      artifact = "${libs.grpc.protoc.gen.java.get()}"
    }
    create("grpc") {
      artifact = "${libs.grpc.protoc.gen.java.get()}"
    }
    create("grpckt") {
      artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar"
    }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins {
        create("kotlin") {
          option("lite")
        }
      }
      task.plugins {
        create("java") {
          option("lite")
        }
        create("grpc") {
          option("lite")
        }
        create("grpckt") {
          option("lite")
        }
      }
    }
  }
}

dependencies {
  api(project(":firebase-common"))
  api(project(":firebase-dataconnect"))
  api(project(":firebase-dataconnect:testutil"))
  api(libs.kotlinx.coroutines.core)

  implementation(project(":protolite-well-known-types"))

  compileOnly(libs.javax.annotation.jsr250)
  implementation(libs.grpc.android)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.stub)
  implementation(libs.protobuf.java.lite)
  implementation(libs.protobuf.kotlin.lite)

  implementation(libs.androidx.test.core)
  implementation(libs.androidx.test.junit)
  implementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions {
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}
