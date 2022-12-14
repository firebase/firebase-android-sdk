import com.google.protobuf.gradle.generateProtoTasks
import com.google.protobuf.gradle.id
import com.google.protobuf.gradle.plugins
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
  id("com.google.protobuf")
  id("firebase-library")
  id("firebase-vendor")
}

// add a dependency on the protoc plugin"s fat jar to make it available to protobuf below.
configurations.create("protobuild")
dependencies {
  "protobuild"(project(":encoders:protoc-gen-firebase-encoders", configuration = "shadow"))
}

protobuf {
  protobuf.protoc {
    val protocVersion = libs.versions.protoc.get()
    artifact = "com.google.protobuf:protoc:$protocVersion"
  }
  protobuf.plugins {
    register("firebaseEncoders") {
      path = configurations["protobuild"].asPath
    }
  }
  protobuf.generateProtoTasks {
    all().all {
      dependsOn(configurations["protobuild"])
      inputs.file("code-gen-cfg.textproto")
      plugins {
        id("firebaseEncoders") {
          option(file("code-gen-cfg.textproto").path)
        }
      }
      builtins {
        remove(create("java"))
      }
    }
  }
}

firebaseLibrary {
  publishJavadoc = false
  testLab {
    enabled = true
    device("model=walleye,version=28") // Pixel2
    device("model=walleye,version=27") // Pixel2
    device("model=victara,version=19") // Moto X
    device("model=Nexus4,version=22")
    device("model=Nexus7,version=21")
    device("model=Nexus4,version=19")
    device("model=starqlteue,version=26") // Galaxy S9
    device("model=m0,version=18") // Galaxy S3
    device("model=hero2lte,version=23") // Galaxy S7
    device("model=htc_m8,version=19") // HTC One (M8)
  }
}

vendor {
  // Integration tests use dagger classes that are not used in the main SDK,
  // so we disable dead code elimination to ensure those classes are preserved.
  optimize.set(false)
}

android {
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject
  compileSdk = targetSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  testOptions.unitTests.isIncludeAndroidResources = true
}

thirdPartyLicenses {
  add("Dagger", "${rootDir}/third_party/licenses/apache-2.0.txt")
}

dependencies {
  implementation(project(":transport:transport-api"))
  implementation(project(":encoders:firebase-encoders"))
  implementation(project(":encoders:firebase-encoders-proto"))
  annotationProcessor(project(":encoders:firebase-encoders-processor"))
  implementation(libs.androidx.annotation)

  implementation("javax.inject:javax.inject:1")
  vendor("com.google.dagger:dagger:2.27") {
    exclude(group = "javax.inject", module = "javax.inject")
  }
  annotationProcessor("com.google.dagger:dagger-compiler:2.27")

  compileOnly(libs.autovalue.annotations)

  annotationProcessor(libs.autovalue)

  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.mockito.core)

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation("org.mockito:mockito-core:2.25.0")
  androidTestImplementation("org.mockito:mockito-android:2.25.0")

  androidTestAnnotationProcessor("com.google.dagger:dagger-compiler:2.27")
}
