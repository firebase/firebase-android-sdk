/*
 * Copyright 2022 Google LLC
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

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.LibraryVariant
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.file.Directory
import org.gradle.api.plugins.PluginManager
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.BuildServiceRegistry
import org.gradle.api.services.BuildServiceSpec
import org.gradle.kotlin.dsl.apply
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkQueue

/**
 * Creates a file at the buildDir for the given [Project].
 *
 * Syntax sugar for:
 * ```
 * project.file("${project.layout.buildDirectory.get().asFile}/$path)
 * ```
 */
fun Project.fileFromBuildDir(path: String) = file("${layout.buildDirectory.get().asFile}/$path")

/**
 * Maps a file provider to another file provider as a sub directory.
 *
 * Syntax sugar for:
 * ```
 * fileProvider.map { File("${it.path}/$path") }
 * ```
 */
fun Provider<File>.childFile(path: String) = map { File("${it.path}/$path") }

/**
 * Returns a new [File] under the given sub directory.
 *
 * Syntax sugar for:
 * ```
 * File("$path/$childPath")
 * ```
 */
fun File.childFile(childPath: String) = File("$path/$childPath")

/**
 * Rewrites the lines of a file.
 *
 * The lines of the file are first read and then transformed by the provided `block` function. The
 * transformed lines are then joined together with a newline character and written back to the file.
 *
 * If the `terminateWithNewline` parameter is set to `false`, the file will not be terminated with a
 * newline character.
 *
 * @param terminateWithNewline Whether to terminate the file with a newline character. Defaults to
 *   `true`.
 * @param block A function that takes a string as input and returns a new string. This function is
 *   used to transform the lines of the file before they are rewritten.
 *
 * ```
 * val file = File("my-file.txt")
 *
 * // Rewrite the lines of the file, replacing all spaces with tabs.
 * file.rewriteLines { it.replace(" ", "\t") }
 *
 * // Rewrite the lines of the file, capitalizing the first letter of each word.
 * file.rewriteLines { it.capitalizeWords() }
 * ```
 *
 * @see [readLines]
 * @see [writeText]
 */
fun File.rewriteLines(terminateWithNewline: Boolean = true, block: (String) -> String) {
  val newLines = readLines().map(block)
  writeText(newLines.joinToString("\n").let { if (terminateWithNewline) it + "\n" else it })
}

/**
 * Provides a temporary file for use during the task.
 *
 * Creates a file under the [temporaryDir][DefaultTask.getTemporaryDir] of the task, and should be
 * preferred to defining an explicit [File]. This will allow Gradle to make better optimizations on
 * our part, and helps us avoid edge-case scenarios like conflicting file names.
 */
fun DefaultTask.tempFile(path: String) = provider { temporaryDir.childFile(path) }

/**
 * Returns a list of children files, or an empty list if this [File] doesn't exist or doesn't have
 * any children.
 *
 * Syntax sugar for:
 * ```
 * listFiles().orEmpty()
 * ```
 */
fun File.listFilesOrEmpty() = listFiles().orEmpty()

/**
 * Copies this file to the specified directory.
 *
 * The new file will retain the same [name][File.getName] and [extension][File.extension] as this
 * file.
 *
 * @param target The directory to copy the file to.
 * @param overwrite Whether to overwrite the file if it already exists.
 * @param bufferSize The size of the buffer to use for the copy operation.
 * @return The new file.
 * @see copyTo
 */
fun File.copyToDirectory(
  target: File,
  overwrite: Boolean = false,
  bufferSize: Int = DEFAULT_BUFFER_SIZE,
): File = copyTo(target.childFile(name), overwrite, bufferSize)

/**
 * Submits a piece of work to be executed asynchronously.
 *
 * More Kotlin friendly variant of the existing [WorkQueue.submit]
 *
 * Syntax sugar for:
 * ```kotlin
 * submit(T::class.java, paramAction)
 * ```
 */
inline fun <reified T : WorkAction<C>, C : WorkParameters> WorkQueue.submit(
  noinline action: C.() -> Unit
) {
  submit(T::class.java, action)
}

/**
 * Creates an attribute of type T.
 *
 * More kotlin friendly variant of the existing [Attribute.of]
 *
 * Syntax sugar for:
 * ```kotlin
 * Attribute.of(name, T::class.java)
 * ```
 */
inline fun <reified T> attributeFrom(name: String) = Attribute.of(name, T::class.java)

/**
 * Sets an attribute value.
 *
 * Syntax sugar for:
 * ```kotlin
 * attribute(Attribute.of(name, T::class.java), value)
 * ```
 */
inline fun <reified T : Any> AttributeContainer.attribute(name: String, value: T) =
  attribute(attributeFrom(name), value)

/**
 * Syntax sugar for:
 * ```kotlin
 * pluginManager.apply(T::class)
 * ```
 */
inline fun <reified T : Any> PluginManager.apply(): Unit = apply(T::class)

/**
 * The name provided to this artifact when published.
 *
 * For example, the following could be an artifact name:
 * ```
 * "com.google.firebase:firebase-common:16.0.5"
 * ```
 */
val Dependency.artifactName: String
  get() = listOfNotNull(group, name, version).joinToString(":")

/**
 * Creates an archive of this directory at the [dest] file.
 *
 * Should only be ran within the context of a [Task], as outside of a [Task] so you should likely be
 * using the `copy` or `sync` tasks instead.
 */
fun File.zipFilesTo(task: Task, dest: File): File {
  val logger = task.logger

  logger.info("Zipping '$absolutePath' to '${dest.absolutePath}'")

  logger.debug("Ensuring parent directories are present for zip file")
  dest.parentFile?.mkdirs()

  logger.debug("Creating empty zip file to write to")
  dest.createNewFile()

  logger.debug("Packing file contents into zip")
  ZipOutputStream(BufferedOutputStream(dest.outputStream())).use { zipFile ->
    for (file in walk().filter { it.isFile }) {
      val relativePath = file.relativeTo(this).unixPath
      logger.debug("Adding file to zip: $relativePath")

      zipFile.putNextEntry(ZipEntry(relativePath))
      file.inputStream().use { it.copyTo(zipFile) }
      zipFile.closeEntry()
    }
  }

  return dest
}

/**
 * Bind a callback to run whenever there are release variants for this android build.
 *
 * Syntax sugar for:
 * ```
 * components.onVariants(components.selector().withBuildType("release")) {
 *  // ...
 * }
 * ```
 *
 * @see LibraryAndroidComponentsExtension.onVariants
 */
fun LibraryAndroidComponentsExtension.onReleaseVariants(
  callback: (variant: LibraryVariant) -> Unit
) {
  onVariants(selector().withBuildType("release"), callback)
}

/**
 * Register a build service under the specified [name], if it hasn't been registered already.
 *
 * ```
 * project.gradle.sharedServices.registerIfAbsent<GMavenService, _>("gmaven")
 * ```
 *
 * @param T The build service class to register
 * @param P The parameters class for the build service to register
 * @param name The name to register the build service under
 * @param config An optional configuration block to setup the build service with
 */
inline fun <reified T : BuildService<P>, reified P : BuildServiceParameters> BuildServiceRegistry
  .registerIfAbsent(name: String, noinline config: BuildServiceSpec<P>.() -> Unit = {}) =
  registerIfAbsent(name, T::class.java, config)

/**
 * The value of this provider if present, or an empty list if it's not present.
 *
 * @return The value of this provider or an empty list.
 */
fun <V, T : List<V>> Provider<T>.orEmpty() = orNull.orEmpty()

/**
 * The value of this provider if present, or an empty map if it's not present.
 *
 * @return The value of this provider or an map list.
 */
fun <K, V, T : Map<K, V>> Provider<T>.orEmpty() = orNull.orEmpty()

/**
 * Maps to the single file (non directory) within this directory, or throws an exception if it can't
 * find a file or if there's more than one file.
 *
 * Helper wrapper around [Directory.nestedFile] for providers.
 */
val Provider<Directory>.nestedFile: Provider<File>
  get() = map { it.nestedFile }

/**
 * Maps to the single file (non directory) within this directory, or throws an exception if it can't
 * find a file or if there's more than one file.
 *
 * Useful in situations where a directory merely acts as a container for a nested file whose name
 * isn't known at compile time.
 *
 * For example, given the following directory structure:
 * ```
 * com/
 *  google/
 *   firebase/
 *    firebase-bom-34.8.0.pom
 * ```
 *
 * This will result in the `firebase-bom-34.8.0.pom` file being returned.
 */
val Directory.nestedFile: File
  get() = asFileTree.single { it.isFile }

/**
 * Creates a delegated provider.
 *
 * Useful to expose a provide via delegated properties. See [tempFile] for a reference usage.
 */
internal fun <T : Any> provider(provider: () -> T) =
  object : ReadOnlyProperty<Any?, T> {
    override operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
      return provider()
    }
  }
