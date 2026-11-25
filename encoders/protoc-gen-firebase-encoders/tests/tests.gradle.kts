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

plugins {
  id("java-library")
  alias(libs.plugins.protobuf)
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
    }
  }
}

dependencies {
  testAnnotationProcessor(project(":encoders:firebase-encoders-processor"))

  testImplementation(project(":encoders:firebase-encoders"))
  testImplementation(project(":encoders:firebase-encoders-proto"))
  testImplementation(libs.protobuf.java)
  testImplementation(libs.truth)
  testImplementation(libs.junit)
}
