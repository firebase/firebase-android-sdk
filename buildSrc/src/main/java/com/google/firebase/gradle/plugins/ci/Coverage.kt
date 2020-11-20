// Copyright 2020 Google LLC
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

package com.google.firebase.gradle.plugins.ci

import com.google.firebase.gradle.plugins.FirebaseLibraryExtension
import java.io.File
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

fun FirebaseLibraryExtension.configureCoverage() {
    project.apply(plugin = "jacoco")
    val jacocoReportsDir = File(project.buildDir, "/reports/jacoco")

    project.extensions.configure(JacocoPluginExtension::class.java) {
        toolVersion = "0.8.5"
        reportsDir = jacocoReportsDir
    }

    project.tasks.withType(Test::class.java) {
        extensions.configure(JacocoTaskExtension::class.java) {
            excludeClassLoaders = listOf("jdk.internal.*")
            isIncludeNoLocationClasses = true
        }
    }

    project.tasks.register("checkCoverage", JacocoReport::class.java) {
        dependsOn("check")
        description = "Generates JaCoCo check coverage report."
        group = "verification"

        val excludes = listOf(
            "**/R.class",
            "**/R$*.class",
            "**/BuildConfig.*",
            "**/proto/**",
            "**Manifest*.*"
        )

        classDirectories.from(
            project.files(
                project.fileTree(
                    mapOf(
                        "dir" to project.buildDir.toString() + "/intermediates/javac/release",
                        "excludes" to excludes
                    )
                ),
                project.fileTree(
                    mapOf(
                        "dir" to project.buildDir.toString() + "/tmp/kotlin-classes/release",
                        "excludes" to excludes
                    )
                )
            )
        )
        sourceDirectories.from(project.files("src/main/java", "src/main/kotlin"))
        executionData.from(
            project.fileTree(
                mapOf(
                    "dir" to project.buildDir,
                    "includes" to listOf("jacoco/*.exec")
                )
            )
        )

        reports {
            html.destination = File(jacocoReportsDir, "${artifactId.get()}/html")
            xml.isEnabled = true
            xml.destination = File(jacocoReportsDir, "${artifactId.get()}.xml")
        }
        outputs.upToDateWhen { false }
    }
}
