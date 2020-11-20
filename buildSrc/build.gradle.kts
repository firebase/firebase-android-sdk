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
    `kotlin-dsl`
    id("org.jlleitschuh.gradle.ktlint") version "9.2.1"
}

repositories {
    maven(url = "https://maven.google.com/")
    jcenter()
    mavenCentral()
    maven(url = "https://storage.googleapis.com/android-ci/mvn/")
}

dependencies {
    implementation("digital.wup:android-maven-publish:3.6.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.72")
    implementation("org.json:json:20180813")
    implementation("io.opencensus:opencensus-api:0.18.0")
    implementation("io.opencensus:opencensus-exporter-stats-stackdriver:0.18.0")
    runtimeOnly("io.opencensus:opencensus-impl:0.18.0")
    implementation("com.google.code.gson:gson:2.8.6")
    implementation("org.jetbrains.dokka:dokka-android-gradle-plugin:0.9.17-g013-9b8280a")
    implementation("com.android.tools.build:gradle:4.0.2")

    testImplementation("junit:junit:4.13-rc-1")
    testImplementation("com.google.truth:truth:1.0.1")
    testImplementation("commons-io:commons-io:2.6")
}

gradlePlugin {
    plugins {
        register("licensePlugin") {
            id = "LicenseResolverPlugin"
            implementationClass = "com.google.firebase.gradle.plugins.license.LicenseResolverPlugin"
        }
        register("publishingPlugin") {
            id = "PublishingPlugin"
            implementationClass = "com.google.firebase.gradle.plugins.publish.PublishingPlugin"
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
    }
}

tasks.test {
    // Make sure output from
    // standard out or error is shown
    // in Gradle output.
    testLogging.showStandardStreams = true

    val enablePluginTests: Boolean? by project
    enabled = enablePluginTests ?: false
}

tasks.validatePlugins {
    enabled = false
}
