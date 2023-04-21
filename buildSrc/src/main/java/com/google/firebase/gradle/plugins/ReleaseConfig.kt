package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.plugins.ReleaseConfig.Companion.fromFile
import java.io.File

/**
 * Container for providing data about a release.
 *
 * Allows a type safe representation of data generated for an official release.
 *
 * @see ReleaseGenerator
 * @see fromFile
 * @see toFile
 *
 * @property releaseName the name of a release, such as `m130`
 * @property libs a list of project paths intending to release
 */
data class ReleaseConfig(val releaseName: String, val libs: Set<String>) {
  companion object {
    /**
     * Parses a [ReleaseConfig] from the contents of a given [File].
     *
     * Allows one to reuse an already generated [ReleaseConfig] that was saved to disc.
     *
     * @param file the [File] to parse
     * @see toFile
     */
    fun fromFile(file: File): ReleaseConfig {
      val contents = file.readLines()
      val libs = contents.filter { it.startsWith(":") }.toSet()
      val releaseName = contents.first { it.startsWith("name") }.substringAfter("=").trim()
      return ReleaseConfig(releaseName, libs)
    }
  }

  /**
   * Converts a [ReleaseConfig] to a [String] to be saved in a [File].
   *
   * An example of the output fomat can be seen below:
   * ```
   * [release]
   * name = m130
   *
   * [modules]
   * :firebase-common
   * :appcheck:firebase-appcheck
   * ```
   *
   * @see fromFile
   */
  fun toFile() =
    """
    |[release]
    |name = $releaseName
                    
    |[modules]
    |${libs.sorted().joinToString("\n")}
    """
      .trimMargin()
}
