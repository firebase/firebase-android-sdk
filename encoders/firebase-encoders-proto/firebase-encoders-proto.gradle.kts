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
  id("firebase-java-library")
  id("com.google.protobuf")
}

firebaseLibrary {
  publishSources = true
  publishJavadoc = false
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

protobuf {
  protobuf.protoc {
    val protocVersion = libs.versions.protoc.get()
    artifact = "com.google.protobuf:protoc:$protocVersion"
  }
}


dependencies {
  implementation(project(":encoders:firebase-encoders"))
  implementation(libs.androidx.annotation)

  annotationProcessor(project(":encoders:firebase-encoders-processor"))

  testAnnotationProcessor(project(":encoders:firebase-encoders-processor"))

  testImplementation("com.google.guava:guava:31.0-jre")
  testImplementation(libs.junit)
  testImplementation("com.google.protobuf:protobuf-java-util:3.11.0")
  testImplementation("com.google.truth.extensions:truth-proto-extension:1.0")
  testImplementation(libs.truth)

}

tasks.withType<JavaCompile> {
  options.compilerArgs.add("-Werror")
}
