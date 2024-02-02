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

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fixes minor inconsistencies between what dackka generates, and what firesite actually expects.
 *
 * Should dackka ever expand to offer configurations for these procedures, this class can be
 * replaced.
 *
 * More specifically, it:
 * - Deletes unnecessary files
 * - Removes Class and Index headers from _toc.yaml files
 * - Adds the deprecated status to ktx sections in _toc.yaml files
 * - Fixes broken hyperlinks in `@see` blocks
 * - Removes the prefix path from book_path
 *
 * **Please note:** This task is idempotent- meaning it can safely be ran multiple times on the same
 * set of files.
 */
@CacheableTask
abstract class FiresiteTransformTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dackkaFiles: Property<File>

  @get:OutputDirectory abstract val outputDirectory: Property<File>

  @TaskAction
  fun build() {
    val namesOfFilesWeDoNotNeed =
      listOf("index.html", "classes.html", "packages.html", "package-list")
    val rootDirectory = dackkaFiles.get()
    val targetDirectory = outputDirectory.get()
    targetDirectory.deleteRecursively()

    rootDirectory.walkTopDown().forEach {
      if (it.name !in namesOfFilesWeDoNotNeed) {
        val relativePath = it.toRelativeString(rootDirectory)
        val newFile = it.copyTo(File("${targetDirectory.path}/$relativePath"), true)

        when (it.extension) {
          "html" -> newFile.fixHTMLFile()
          "yaml" -> newFile.fixYamlFile()
        }
      }
    }
  }

  private fun File.fixHTMLFile() {
    val fixedContent = readText().fixBookPath().fixHyperlinksInSeeBlocks()
    writeText(fixedContent)
  }

  private fun File.fixYamlFile() {
    val fixedContent = readText().removeClassHeader().removeIndexHeader().addDeprecatedStatus()
    writeText(fixedContent)
  }

  /**
   * Fixes broken hyperlinks in the rendered HTML
   *
   * Links in Dockka are currently broken in `@see` tags. This transform destructures those broken
   * links and reconstructs them as they're expected to be.
   *
   * Example input:
   * ```
   * // Generated from @see <a href="git.page.link/timestamp-proto">Timestamp</a>The ref timestamp definition
   * <code>&lt;a href=&quot;git.page.link/timestamp-proto&quot;&gt;Timestamp&lt;/a&gt;The ref timestamp definition</code></td>
   * <td></td>
   * ```
   *
   * Example output:
   * ```
   * <code><a href="git.page.link/timestamp-proto">Timestamp</a></code></td>
   * <td>The ref timestamp definition</td>
   * ```
   */
  // TODO(go/dokka-upstream-bug/2665): Remove when Dockka fixes this issue
  private fun String.fixHyperlinksInSeeBlocks() =
    replace(
      Regex(
        "<code>&lt;a href=&quot;(?<href>.*)&quot;&gt;(?<link>.*)&lt;/a&gt;(?<text>.*)</code></td>\\s*<td></td>"
      )
    ) {
      val (href, link, text) = it.destructured

      """
                <code><a href="$href">$link</a></code></td>
                <td>$text</td>
            """
        .trimIndent()
    }

  /**
   * Adds the deprecated status to ktx libs.
   *
   * Our ktx libs are marked as deprecated in the sidebar, and as such- require that we add a
   * `status: deprecated` to their section in the relevant `_toc.yaml` file.
   *
   * Example input:
   * ```
   * - title: "firebase.database.ktx"
   *   path: "/docs/reference/android/com/google/firebase/database/ktx/package-summary.html"
   * ```
   *
   * Example output:
   * ```
   * - title: "firebase.database.ktx"
   *   status: deprecated
   *   path: "/docs/reference/android/com/google/firebase/database/ktx/package-summary.html"
   * ```
   */
  // TODO(b/310964911): Remove when we drop ktx modules
  private fun String.addDeprecatedStatus(): String =
    replace(Regex("- title: \"(.+ktx)\"")) {
      val packageName = it.firstCapturedValue

      """
      - title: "${packageName}"
        status: deprecated
    """
        .trimIndent()
    }

  // We don't actually upload class or index files,
  // so these headers will throw not found errors if not removed.
  // TODO(b/243674302): Remove when dackka exposes configuration for this
  private fun String.removeClassHeader() =
    remove(Regex("- title: \"Class Index\"\n {2}path: \".+\"\n\n"))

  private fun String.removeIndexHeader() =
    remove(Regex("- title: \"Package Index\"\n {2}path: \".+\"\n\n"))

  // We use a common book for all sdks, wheres dackka expects each sdk to have its own book.
  // TODO(b/243674303): Remove when dackka exposes configuration for this
  private fun String.fixBookPath() =
    remove(Regex("(?<=setvar book_path ?%})(.+)(?=/_book.yaml\\{% ?endsetvar)"))
}
