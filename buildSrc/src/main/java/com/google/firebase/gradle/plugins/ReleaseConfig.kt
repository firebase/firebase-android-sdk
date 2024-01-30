/*
 * Copyright 2023 Google LLC
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Container for providing data about a release.
 *
 * Allows a type safe representation of data generated for an official release.
 *
 * @see ReleaseGenerator
 * @see fromFile
 * @see toFile
 *
 * @property name the name of a release, such as `m130`
 * @property libraries a list of project paths intending to release
 */
@Serializable
data class ReleaseConfig(val name: String, val libraries: List<String>) {

  /**
   * Writes a [ReleaseConfig] into a [File] as JSON.
   *
   * An example of the output can be seen below:
   * ```
   * {
   *   "name": "m130",
   *   "libraries": [
   *     ":firebase-appdistribution",
   *     ":firebase-config",
   *   ]
   * }
   * ```
   *
   * @see fromFile
   */
  fun toFile(file: File) = file.also { it.writeText(formatter.encodeToString(this)) }

  companion object {
    val formatter = Json { prettyPrint = true }

    /**
     * Parses a [ReleaseConfig] from the contents of a given [File].
     *
     * Allows one to reuse an already generated [ReleaseConfig] that was saved to disc.
     *
     * @param file the [File] to parse
     * @see toFile
     */
    fun fromFile(file: File): ReleaseConfig = formatter.decodeFromString(file.readText())
  }
}
