/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.diffplug.gradle.spotless.SpotlessPlugin
import java.util.regex.Pattern

plugins {
  alias(libs.plugins.spotless)
  alias(libs.plugins.protobuf) apply false
  alias(libs.plugins.errorprone)
  alias(libs.plugins.crashlytics) apply false
  id("PublishingPlugin")
  id("firebase-ci")
  id("smoke-tests")
  alias(libs.plugins.google.services)
  alias(libs.plugins.kotlinx.serialization) apply false
}

extra["targetSdkVersion"] = 34

extra["compileSdkVersion"] = 34

extra["minSdkVersion"] = 23

firebaseContinuousIntegration {
  ignorePaths =
    listOf(
      Pattern.compile(".*\\.gitignore$"),
      Pattern.compile(".*\\/.*.md$"),
      Pattern.compile(".*\\.gitignore$"),
    )
}

fun Project.applySpotless() {
  apply<SpotlessPlugin>()
  spotless {
    java {
      target("src/**/*.java")
      targetExclude("**/test/resources/**")
      googleJavaFormat("1.22.0").reorderImports(true).skipJavadocFormatting()
    }
    kotlin {
      target("src/**/*.kt")
      ktfmt("0.41").googleStyle()
    }
    kotlinGradle {
      target("*.gradle.kts") // default target for kotlinGradle
      ktfmt("0.41").googleStyle()
    }
    format("styling") {
      target("**/*.md")
      prettier().config(mapOf("printWidth" to 100, "proseWrap" to "always"))
    }
  }
}

applySpotless()

configure(subprojects) { applySpotless() }

tasks.named("clean") { delete(rootProject.layout.buildDirectory) }

apply(from = "gradle/errorProne.gradle")
