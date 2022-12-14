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
  id("org.jetbrains.kotlin.jvm")
  id("java-library")
  id("com.github.johnrengelman.shadow") version "5.2.0"
  id("com.google.protobuf")
  id("kotlin-kapt")
}

protobuf {
  protobuf.protoc {
    val protocVersion = libs.versions.protoc.get()
    artifact = "com.google.protobuf:protoc:$protocVersion"
  }
}

kapt {
  generateStubs = false
  correctErrorTypes = true
}

tasks.jar {
  manifest {
    attributes("Main-Class" to "com.google.firebase.encoders.proto.codegen.MainKt")
  }
}

dependencies {

  implementation("com.google.protobuf:protobuf-java:3.21.9")
  implementation("com.squareup:javapoet:1.13.0")
  implementation("com.google.guava:guava:30.0-jre")
  implementation(libs.dagger.dagger)
  kapt(libs.dagger.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
}
