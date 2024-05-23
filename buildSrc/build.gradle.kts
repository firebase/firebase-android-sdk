// Copyright 2018 Google LLC
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
    id("com.ncorti.ktfmt.gradle") version "0.11.0"
    kotlin("plugin.serialization") version "1.8.22"
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    maven(url = "https://maven.google.com/")
    mavenCentral()
    maven(url = "https://storage.googleapis.com/android-ci/mvn/")
    maven(url = "https://plugins.gradle.org/m2/")
}

// Refer latest "perf-plugin" released version on https://maven.google.com/web/index.html?q=perf-plugin#com.google.firebase:perf-plugin
// The System property allows us to integrate with an unreleased version from https://bityl.co/3oYt.
// Refer go/fireperf-plugin-test-on-head for more details.
val perfPluginVersion = System.getenv("FIREBASE_PERF_PLUGIN_VERSION") ?: "1.4.1"

ktfmt {
    googleStyle()
}

dependencies {
    // Firebase performance plugin, it should be added here because of how gradle dependency
    // resolution works, otherwise it breaks Fireperf Test Apps.
    // See https://github.com/gradle/gradle/issues/12286
    implementation("com.google.firebase:perf-plugin:$perfPluginVersion")
    implementation("com.google.auto.value:auto-value-annotations:1.8.1")
    annotationProcessor("com.google.auto.value:auto-value:1.6.5")
    implementation(kotlin("gradle-plugin", "1.8.22"))
    implementation("org.json:json:20210307")

    implementation("org.eclipse.aether:aether-api:1.0.0.v20140518")
    implementation("org.eclipse.aether:aether-util:1.0.0.v20140518")
    implementation("org.ow2.asm:asm-tree:9.5")
    implementation("org.eclipse.aether:aether-impl:1.0.0.v20140518")
    implementation("org.eclipse.aether:aether-connector-basic:1.0.0.v20140518")
    implementation("org.eclipse.aether:aether-transport-file:1.0.0.v20140518")
    implementation("org.eclipse.aether:aether-transport-http:1.0.0.v20140518")
    implementation("org.eclipse.aether:aether-transport-wagon:1.0.0.v20140518")
    implementation("org.apache.maven:maven-aether-provider:3.3.9")

    implementation("org.eclipse.jgit:org.eclipse.jgit:6.3.0.202209071007-r")

    implementation(libs.kotlinx.serialization.json)
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.android.tools.build:gradle:8.2.1")
    implementation("com.android.tools.build:builder-test-api:8.2.1")

    testImplementation(libs.bundles.kotest)
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.google.truth:truth:1.4.2")
    testImplementation("commons-io:commons-io:2.15.1")
}

gradlePlugin {
    plugins {
        register("licensePlugin") {
            id = "LicenseResolverPlugin"
            implementationClass = "com.google.firebase.gradle.plugins.license.LicenseResolverPlugin"
        }
        register("multiProjectReleasePlugin") {
            id = "MultiProjectReleasePlugin"
            implementationClass = "com.google.firebase.gradle.MultiProjectReleasePlugin"
        }
        register("publishingPlugin") {
            id = "PublishingPlugin"
            implementationClass = "com.google.firebase.gradle.plugins.PublishingPlugin"
        }
        register("firebaseLibraryPlugin") {
            id = "firebase-library"
            implementationClass = "com.google.firebase.gradle.plugins.FirebaseLibraryPlugin"
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
    val enablePluginTests: String? by rootProject
    enabled = enablePluginTests == "true"
}
