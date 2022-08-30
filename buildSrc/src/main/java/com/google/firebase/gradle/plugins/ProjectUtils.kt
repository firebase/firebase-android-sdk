// Copyright 2021 Google LLC
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
package com.google.firebase.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.attributes.Attribute

fun Project.isAndroid(): Boolean =
        listOf("com.android.application", "com.android.library", "com.android.test")
                .any { plugin: String -> project.plugins.hasPlugin(plugin) }

fun toBoolean(value: Any?): Boolean {
    val trimmed = value?.toString()?.trim()?.toLowerCase()
    return "true" == trimmed || "y" == trimmed || "1" == trimmed
}

/**
 * Finds or creates the javadocClasspath [Configuration].
 */
val Project.javadocConfig: Configuration
    get() = configurations.findByName("javadocClasspath") ?: configurations.create("javadocClasspath")

/**
 * Finds or creates the dackkaArtifacts [Configuration].
 *
 * Used to fetch the dackka-fat jar at runtime versus needing to explicitly specify it in project
 * dependencies.
 */
val Project.dackkaConfig: Configuration
    get() =
        configurations.findByName("dackkaArtifacts") ?: configurations.create("dackkaArtifacts") {
            dependencies.add(this@dackkaConfig.dependencies.create("com.google.devsite:dackka-fat:1.0.1"))
        }

/**
 * Fetches the jars of dependencies associated with this configuration through an artifact view.
 */
fun Configuration.getJars() = incoming.artifactView {
    attributes {
        // TODO(b/241795594): replace value with android-class instead of jar after agp upgrade
        attribute(Attribute.of("artifactType", String::class.java), "jar")
    }
}.artifacts.artifactFiles
