/*
 * Copyright 2018 Google LLC
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
  alias(libs.plugins.kotlinx.serialization)
  alias(libs.plugins.spotless)
  `kotlin-dsl`
}

repositories {
  mavenLocal()
  maven(url = "https://maven.google.com/")
  mavenCentral()
  maven(url = "https://storage.googleapis.com/android-ci/mvn/")
  maven(url = "https://plugins.gradle.org/m2/")
}

group = "com.google.firebase"

spotless {
  java {
    target("src/**/*.java")
    targetExclude("**/test/resources/**")
    googleJavaFormat("1.22.0").reorderImports(true).skipJavadocFormatting()
  }
  kotlin {
    target("src/**/*.kt")
    ktfmt("0.52").googleStyle()
  }
}

// Refer latest "perf-plugin" released version on
// https://maven.google.com/web/index.html?q=perf-plugin#com.google.firebase:perf-plugin
// The System property allows us to integrate with an unreleased version from https://bityl.co/3oYt.
// Refer go/fireperf-plugin-test-on-head for more details.
val perfPluginVersion = System.getenv("FIREBASE_PERF_PLUGIN_VERSION") ?: "1.4.1"

dependencies {
  // Firebase performance plugin, it should be added here because of how gradle dependency
  // resolution works, otherwise it breaks Fireperf Test Apps.
  // See https://github.com/gradle/gradle/issues/12286
  implementation("com.google.firebase:perf-plugin:$perfPluginVersion")
  implementation("com.google.auto.value:auto-value-annotations:1.8.1")
  annotationProcessor("com.google.auto.value:auto-value:1.6.5")
  implementation(kotlin("gradle-plugin", "1.8.22"))
  implementation(libs.org.json)
  implementation(libs.bundles.maven.resolver)

  implementation("com.google.guava:guava:33.5.0-jre")
  implementation("org.ow2.asm:asm-tree:9.8")
  implementation("org.eclipse.jgit:org.eclipse.jgit:7.1.0.202411261347-r")
  implementation(libs.kotlinx.serialization.json)
  implementation("com.google.code.gson:gson:2.13.1")
  implementation(libs.android.gradlePlugin.gradle)
  implementation(libs.android.gradlePlugin.builder.test.api)
  implementation("io.github.pdvrieze.xmlutil:serialization-jvm:0.90.3") {
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-json")
    exclude("org.jetbrains.kotlinx", "kotlinx-serialization-core")
  }

  testImplementation(gradleTestKit())
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.mockk)
  testImplementation(libs.junit)
  testImplementation(libs.truth)
  testImplementation("commons-io:commons-io:2.20.0")
  testImplementation(kotlin("test"))
}

gradlePlugin {
  plugins {
    register("licensePlugin") {
      id = "LicenseResolverPlugin"
      implementationClass = "com.google.firebase.gradle.plugins.license.LicenseResolverPlugin"
    }
    register("continuousIntegrationPlugin") {
      id = "firebase-ci"
      implementationClass = "com.google.firebase.gradle.plugins.ci.ContinuousIntegrationPlugin"
    }
    register("smokeTestsPlugin") {
      id = "smoke-tests"
      implementationClass = "com.google.firebase.gradle.plugins.ci.SmokeTestsPlugin"
    }
    register("publishingPlugin") {
      id = "PublishingPlugin"
      implementationClass = "com.google.firebase.gradle.plugins.PublishingPlugin"
    }
    register("firebaseLibraryPlugin") {
      id = "firebase-library"
      implementationClass = "com.google.firebase.gradle.plugins.FirebaseAndroidLibraryPlugin"
    }
    register("firebaseJavaLibraryPlugin") {
      id = "firebase-java-library"
      implementationClass = "com.google.firebase.gradle.plugins.FirebaseJavaLibraryPlugin"
    }
    register("firebaseVendorPlugin") {
      id = "firebase-vendor"
      implementationClass = "com.google.firebase.gradle.plugins.VendorPlugin"
    }
    register("copyGoogleServicesPlugin") {
      id = "copy-google-services"
      implementationClass = "com.google.firebase.gradle.plugins.CopyGoogleServicesPlugin"
    }
  }
}

tasks.withType<Test> {
  testLogging {
    // Make sure output from standard out or error is shown in Gradle output.
    showStandardStreams = true
  }
}
