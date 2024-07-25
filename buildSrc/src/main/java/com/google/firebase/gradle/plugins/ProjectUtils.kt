/*
 * Copyright 2021 Google LLC
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

package com.google.firebase.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType

/** Checks if the project has any of the common Android specific plugins. */
fun Project.isAndroid(): Boolean =
  listOf("com.android.application", "com.android.library", "com.android.test").any {
    project.plugins.hasPlugin(it)
  }

fun toBoolean(value: Any?): Boolean {
  val trimmed = value?.toString()?.trim()?.toLowerCase()
  return "true" == trimmed || "y" == trimmed || "1" == trimmed
}

/**
 * Finds or creates the javadocClasspath [Configuration].
 *
 * This config allows us to expose artifacts on the classpath during doc generation, but not
 * elsewhere.
 */
val Project.javadocConfig: Configuration
  get() =
    configurations.findByName("javadocClasspath")
      ?: configurations.create("javadocClasspath").also {
        configurations.all {
          if (name == "compileOnly") {
            it.extendsFrom(this)
          }
        }
      }

/**
 * Finds or creates the dackkaArtifacts [Configuration].
 *
 * Used to fetch the dackka-fat jar at runtime versus needing to explicitly specify it in project
 * dependencies.
 */
val Project.dackkaConfig: Configuration
  get() =
    configurations.findByName("dackkaArtifacts")
      ?: configurations.create("dackkaArtifacts") {
        dependencies.add(
          this@dackkaConfig.dependencies.create("com.google.devsite:dackka-fat:1.2.0")
        )
      }

/**
 * The [FirebaseLibraryExtension] for this [Project].
 *
 * Syntax sugar for:
 * ```kotlin
 * extensions.getByType<FirebaseLibraryExtension>()
 * ```
 */
val Project.firebaseLibrary: FirebaseLibraryExtension
  get() = extensions.getByType<FirebaseLibraryExtension>()

/**
 * The [FirebaseLibraryExtension] for this [Project], or null if not found.
 *
 * Syntax sugar for:
 * ```kotlin
 * extensions.findByType<FirebaseLibraryExtension>()
 * ```
 */
val Project.firebaseLibraryOrNull: FirebaseLibraryExtension?
  get() = extensions.findByType<FirebaseLibraryExtension>()

/**
 * Hacky-check to see if the module is a ktx variant.
 *
 * This does NOT include Kotlin only SDKs. A KTX library refers to the interop modules suffixed with
 * "ktx". Ironically, this is all this method does. It checks if the module's artifactId ends with
 * "ktx"
 */
val Project.isKTXLibary: Boolean
  get() = firebaseLibrary.artifactId.get().endsWith("-ktx")

/**
 * Provides a project property as the specified type, or null otherwise.
 *
 * Utilizing a safe cast, an attempt is made to cast the project property (if found) to the receiver
 * type. Should this cast fail, the resulting value will be null.
 *
 * Keep in mind that is provided lazily via a [Provider], so evalutation does not occur until the
 * value is needed.
 *
 * @param property the name of the property to look for
 */
inline fun <reified T> Project.provideProperty(property: String) = provider {
  val maybeProperty = findProperty(property)

  // using when instead of an if statement so we can expand as needed
  when (T::class) {
    Boolean::class -> maybeProperty?.toString()?.toBoolean() as? T
    else -> maybeProperty as? T
  }
}

/** Fetches the jars of dependencies associated with this configuration through an artifact view. */
val Configuration.jars: FileCollection
  get() =
    incoming
      .artifactView { attributes { attribute("artifactType", "android-classes") } }
      .artifacts
      .artifactFiles

/** Utility method to call [Task.mustRunAfter] and [Task.dependsOn] on the specified task */
fun <T : Task, R : Task> T.dependsOnAndMustRunAfter(otherTask: R) {
  mustRunAfter(otherTask)
  dependsOn(otherTask)
}

/** Utility method to call [Task.mustRunAfter] and [Task.dependsOn] on the specified task */
fun <T : Task, R : Task> T.dependsOnAndMustRunAfter(otherTask: Provider<R>) {
  mustRunAfter(otherTask)
  dependsOn(otherTask)
}

/** Utility method to call [Task.mustRunAfter] and [Task.dependsOn] on the specified task name */
fun <T : Task> T.dependsOnAndMustRunAfter(otherTask: String) {
  mustRunAfter(otherTask)
  dependsOn(otherTask)
}
