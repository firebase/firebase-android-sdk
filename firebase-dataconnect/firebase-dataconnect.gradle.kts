/*
 * Copyright 2024 Google LLC
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

import com.google.firebase.dataconnect.gradle.plugin.DataConnectExecutableVersionsRegistry
import com.google.firebase.dataconnect.gradle.plugin.UpdateDataConnectExecutableVersionsTask
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
  id("firebase-library")
  id("kotlin-android")
  id("com.google.protobuf")
  id("copy-google-services")
  alias(libs.plugins.kotlinx.serialization)
  id("com.google.firebase.dataconnect.gradle.plugin") apply false
}

firebaseLibrary {
  libraryGroup = "dataconnect"
  testLab.enabled = false
  publishJavadoc = true
  onlyPublishKotlindoc = true
  releaseNotes {
    name.set("{{data_connect_short}}")
    versionName.set("data-connect")
  }
}

android {
  val compileSdkVersion: Int by rootProject
  val targetSdkVersion: Int by rootProject
  val minSdkVersion: Int by rootProject

  namespace = "com.google.firebase.dataconnect"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }

  @Suppress("UnstableApiUsage")
  testOptions {
    targetSdk = targetSdkVersion
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  lint { targetSdk = targetSdkVersion }

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_1_8
    optIn.add("kotlin.RequiresOptIn")
  }
}

protobuf {
  protoc { artifact = "${libs.protoc.get()}" }
  plugins {
    create("java") { artifact = "${libs.grpc.protoc.gen.java.get()}" }
    create("grpc") { artifact = "${libs.grpc.protoc.gen.java.get()}" }
    create("grpckt") { artifact = "${libs.grpc.protoc.gen.kotlin.get()}:jdk8@jar" }
  }
  generateProtoTasks {
    all().forEach { task ->
      task.builtins { create("kotlin") { option("lite") } }
      task.plugins {
        create("java") { option("lite") }
        create("grpc") { option("lite") }
        create("grpckt") { option("lite") }
      }
    }
  }
}

dependencies {
  api(libs.firebase.common)

  implementation(libs.firebase.annotations)
  implementation("com.google.firebase:firebase-appcheck-interop:17.1.0")
  implementation("com.google.firebase:firebase-auth-interop:20.0.0")
  implementation(libs.firebase.components)

  compileOnly(libs.javax.annotation.jsr250)
  compileOnly(libs.kotlinx.datetime)
  implementation(libs.grpc.android)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.stub)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.core)
  implementation(libs.protobuf.java.lite)
  implementation(libs.protobuf.kotlin.lite)

  testCompileOnly(libs.protobuf.java)
  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotest.property)
  testImplementation(libs.kotest.property.arbs)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.kotlinx.datetime)
  testImplementation(libs.kotlinx.serialization.json)
  testImplementation(libs.mockk)
  testImplementation(libs.testonly.three.ten.abp)
  testImplementation(libs.robolectric)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:connectors"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  androidTestImplementation("com.google.firebase:firebase-appcheck:18.0.0")
  androidTestImplementation("com.google.firebase:firebase-auth:22.3.1")
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotest.assertions)
  androidTestImplementation(libs.kotest.property)
  androidTestImplementation(libs.kotest.property.arbs)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.kotlinx.datetime)
  androidTestImplementation(libs.mockk)
  androidTestImplementation(libs.mockk.android)
  androidTestImplementation(libs.testonly.three.ten.abp)
  androidTestImplementation(libs.turbine)
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinJvmCompile>().configureEach {
  if (!name.contains("test", ignoreCase = true)) {
    compilerOptions.freeCompilerArgs.add("-Xexplicit-api=strict")
  }
}

// Registers a Gradle task that updates the JSON file that stores the list of Data Connect
// executable versions. The task gets the list of all versions from the Internet and then
// updates the JSON file with their sizes and hashes.
tasks.register<UpdateDataConnectExecutableVersionsTask>("updateJson") {
  jsonFile.set(
    file("gradleplugin/plugin/src/main/resources/${DataConnectExecutableVersionsRegistry.PATH}")
  )
  workDirectory.set(project.layout.buildDirectory.dir("updateJson"))
}
