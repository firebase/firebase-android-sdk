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
  id("firebase-library")
  alias(libs.plugins.protobuf)
}

firebaseLibrary {
  publishJavadoc = false
  releaseNotes { enabled.set(false) }
}

protobuf {
  protoc { artifact = "${libs.protoc.get()}" }
  generateProtoTasks {
    all().forEach { task ->
      task.addIncludeDir(files("fix-javalite/proto"))

      task.builtins { maybeCreate("java").apply { option("lite") } }
    }
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "firebase.com.protolitewrapper"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    consumerProguardFiles("proguard-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
    }
  }

  sourceSets {
    named("main") { resources.setSrcDirs(listOf("build/extracted-protos/main/", "src/main/proto")) }
  }

  testOptions { targetSdk = targetSdkVersion }
}

dependencies {
  protobuf(libs.proto.google.common.protos) {
    exclude(group = "com.google.protobuf", module = "protobuf-java")
  }

  implementation(libs.protobuf.java.lite)
}
