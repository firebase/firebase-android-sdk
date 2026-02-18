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

plugins {
  `java-gradle-plugin`
  alias(firebaseLibs.plugins.kotlin.jvm)
  alias(firebaseLibs.plugins.kotlinx.serialization)
  alias(firebaseLibs.plugins.spotless)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
  compileOnly(firebaseLibs.android.gradlePlugin.gradle.api)
  implementation(gradleKotlinDsl())
  implementation(firebaseLibs.kotlinx.serialization.core)
  implementation(firebaseLibs.kotlinx.serialization.json)
  implementation("com.google.cloud:google-cloud-storage:2.63.0")
  implementation("io.github.z4kn4fein:semver:3.0.0")
}

gradlePlugin {
  plugins {
    create("dataconnect") {
      id = "com.google.firebase.dataconnect.gradle.plugin"
      implementationClass = "com.google.firebase.dataconnect.gradle.plugin.DataConnectGradlePlugin"
    }
  }
}

spotless {
  kotlin { ktfmt("0.41").googleStyle() }
  kotlinGradle {
    target("*.gradle.kts")
    ktfmt("0.41").googleStyle()
  }
}
