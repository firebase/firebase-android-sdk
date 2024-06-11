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

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location

plugins {
  id("firebase-library")
  id("kotlin-android")
  id("com.google.protobuf")
  id("copy-google-services")
  id("org.jetbrains.dokka") version "1.9.10"
  id("org.jetbrains.kotlin.plugin.serialization") version "1.8.0"
}

firebaseLibrary {
  libraryGroup("dataconnect")
  testLab.enabled = false
  publishSources = true
  publishJavadoc = false
  previewMode = "alpha"
}

android {
  val compileSdkVersion : Int by rootProject
  val targetSdkVersion : Int by rootProject
  val minSdkVersion : Int by rootProject

  namespace = "com.google.firebase.dataconnect"
  compileSdk = compileSdkVersion
  defaultConfig {
    minSdk = minSdkVersion
    targetSdk = targetSdkVersion
    multiDexEnabled = true
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
  }
  kotlinOptions { jvmTarget = "1.8" }

  @Suppress("UnstableApiUsage")
  testOptions {
    unitTests {
      isIncludeAndroidResources = true
      isReturnDefaultValues = true
    }
  }

  packaging {
    resources {
      excludes.add("META-INF/LICENSE.md")
      excludes.add("META-INF/LICENSE-notice.md")
    }
  }
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
  api("com.google.firebase:firebase-common:21.0.0")

  api(libs.kotlinx.coroutines.core)
  api(libs.kotlinx.serialization.core)

  implementation("com.google.firebase:firebase-annotations:16.2.0")
  implementation("com.google.firebase:firebase-components:18.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
  }
  implementation(project(":protolite-well-known-types"))
  implementation("com.google.firebase:firebase-auth-interop:20.0.0") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }

  compileOnly(libs.javax.annotation.jsr250)
  implementation(libs.grpc.android)
  implementation(libs.grpc.okhttp)
  implementation(libs.grpc.protobuf.lite)
  implementation(libs.grpc.kotlin.stub)
  implementation(libs.grpc.stub)
  implementation(libs.protobuf.java.lite)
  implementation(libs.protobuf.kotlin.lite)

  testCompileOnly(libs.protobuf.java)
  testImplementation(project(":firebase-dataconnect:testutil"))
  testImplementation(libs.mockk)
  testImplementation(libs.robolectric)
  testImplementation(libs.truth)
  testImplementation(libs.truth.liteproto.extension)
  testImplementation(libs.kotlin.coroutines.test)
  testImplementation(libs.kotlinx.serialization.json)

  androidTestImplementation(project(":firebase-dataconnect:androidTestutil"))
  androidTestImplementation(project(":firebase-dataconnect:connectors"))
  androidTestImplementation(project(":firebase-dataconnect:testutil"))
  androidTestImplementation("com.google.firebase:firebase-auth:22.3.1") {
    exclude(group = "com.google.firebase", module = "firebase-common")
    exclude(group = "com.google.firebase", module = "firebase-components")
  }
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.kotlin.coroutines.test)
  androidTestImplementation(libs.truth)
  androidTestImplementation(libs.truth.liteproto.extension)
  androidTestImplementation(libs.turbine)
}

tasks.withType<KotlinCompile>().all {
  kotlinOptions {
    freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
  }
}

extra["packageName"] = "com.google.firebase.dataconnect"

tasks.withType<DokkaTask>().configureEach {
  moduleName.set("firebase-dataconnect")
  val cacheRootDirectory = layout.buildDirectory.dir("dokka/cache")
  cacheRootDirectory.get().asFile.mkdirs()
  cacheRoot.set(cacheRootDirectory)
  mustRunAfter("ktfmtFormat")
}

// Enable Kotlin "Explicit API Mode". This causes the Kotlin compiler to fail if any
// classes, methods, or properties have implicit `public` visibility. This check helps
// avoid  accidentally leaking elements into the public API, requiring that any public
// element be explicitly declared as `public`.
// https://github.com/Kotlin/KEEP/blob/master/proposals/explicit-api-mode.md
// https://chao2zhang.medium.com/explicit-api-mode-for-kotlin-on-android-b8264fdd76d1
tasks.withType<KotlinCompile>().all {
  if (!name.contains("test", ignoreCase = true)) {
    if (!kotlinOptions.freeCompilerArgs.contains("-Xexplicit-api=strict")) {
      kotlinOptions.freeCompilerArgs += "-Xexplicit-api=strict"
    }
  }
}

// Runs dokkaHtml and starts a web server to serve it locally.
tasks.register("dokkaHtmlServe") {
  group = "documentation"
  description = "Run a web server to serve the HTML output of the dokkaHtml task"
  mustRunAfter("dokkaHtml")

  doLast {
    val port = 8000
    val directory = layout.buildDirectory.dir("dokka/html").get().asFile.absolutePath

    val javelin = Javalin.create { javalinConfig ->
      javalinConfig.staticFiles.add { staticFileConfig ->
        staticFileConfig.directory = directory
        staticFileConfig.location = Location.EXTERNAL
        staticFileConfig.hostedPath = "/"
      }
    }

    javelin.start(port)
    println("Starting HTTP server at http://localhost:$port which serves the contents of directory: $directory")
    println("Press ENTER to stop the server")
    readlnOrNull()
    println("Stopping HTTP server at http://localhost:$port")
    javelin.stop()
  }
}
