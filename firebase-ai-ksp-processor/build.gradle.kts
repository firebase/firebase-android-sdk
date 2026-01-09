/*
 * Copyright 2025 Google LLC
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

plugins {
  kotlin("jvm")
  id("java-library")
  id("maven-publish")
}

dependencies {
  testImplementation(kotlin("test"))
  implementation(libs.symbol.processing.api)
  implementation(libs.kotlinpoet.ksp)
}

tasks.test { useJUnitPlatform() }

kotlin { jvmToolchain(17) }

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

publishing {
  publications {
    create<MavenPublication>("mavenKotlin") {
      from(components["kotlin"])
      groupId = "com.google.firebase"
      artifactId = "firebase-ai-processor"
      version = "1.0.0"
    }
  }
  repositories {
    maven { url = uri("m2/") }
    mavenLocal()
  }
}
