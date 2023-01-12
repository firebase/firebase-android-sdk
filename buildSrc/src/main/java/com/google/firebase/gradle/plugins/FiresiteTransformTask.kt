// Copyright 2022 Google LLC
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

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
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
 * - Changes links to be appropriate for Firesite versus normal Devsite behavior
 * - Fixes broken hyperlinks in `@see` blocks
 * - Removes the prefix path from book_path
 * - Removes the google groupId for Javadocs
 * - Changes the path for _reference-head-tags at the top of html files
 *
 * **Please note:** This task is idempotent- meaning it can safely be ran multiple times on the same
 * set of files.
 */
@CacheableTask
abstract class FiresiteTransformTask : DefaultTask() {
  @get:InputDirectory
  @get:PathSensitive(PathSensitivity.RELATIVE)
  abstract val dackkaFiles: Property<File>

  @get:Input abstract val referenceHeadTagsPath: Property<String>

  @get:Input abstract val referencePath: Property<String>

  @get:Input @get:Optional abstract val removeGoogleGroupId: Property<Boolean>

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
    val fixedContent =
      readText().fixBookPath().fixReferenceHeadTagsPath().fixLinks().fixHyperlinksInSeeBlocks()
    writeText(fixedContent)
  }

  private fun File.fixYamlFile() {
    val fixedContent =
      readText().removeClassHeader().removeIndexHeader().fixLinks().let {
        if (removeGoogleGroupId.getOrElse(false)) it.removeGoogleGroupId() else it
      }
    writeText(fixedContent)
  }

  /**
   * Removes the leading `com.google` group id from strings in the file
   *
   * We have internal SDKs that generate their docs outside the scope of this plugin. The Javadoc
   * variant of those SDks is typically generated with metalava- which does *not* provide the
   * groupId. This makes the output look weird, as not all SDKs line up. So this method exists to
   * correct Javadoc nav files, so that they align with internally generated docs.
   *
   * Example input:
   * ```
   * "com.google.firebase.appcheck"
   * ```
   * Example output:
   * ```
   * "firebase.appcheck"
   * ```
   */
  // TODO(b/257293594): Remove when dackka exposes configuration for this
  private fun String.removeGoogleGroupId() = remove(Regex("(?<=\")com.google.(?=firebase.)"))

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

  // Our documentation does not live under the standard path expected by Dackka, especially
  // between Kotlin + Javadocs
  // TODO(b/243674305): Remove when dackka exposes configuration for this
  private fun String.fixLinks() =
    replace(Regex("(?<=\")/reference[^\"]*?(?=/com/google/firebase)"), referencePath.get())

  // We utilize difference reference head tags between Kotlin and Java docs
  // TODO(b/248316730): Remove when dackka exposes configuration for this
  private fun String.fixReferenceHeadTagsPath() =
    replace(
      Regex("(?<=include \").*(?=/_reference-head-tags.html\" %})"),
      referenceHeadTagsPath.get()
    )

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
