// Copyright 2023 Google LLC
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

/**
 * Possible version types in a SemVer versioning scheme.
 *
 * @see ModuleVersion
 */
enum class VersionType {
  MAJOR,
  MINOR,
  PATCH
}

/**
 * Type-Safe representation of your standard [SemVer](https://semver.org/) versioning scheme.
 *
 * Additional labels for pre-release builds are not supported at this time. All versions should fall
 * under your standard `MAJOR.MINOR.PATCH` format.
 *
 * @see fromString
 * @see fromStringOrNull
 *
 * @property major An update that represents breaking changes
 * @property minor An update that represents new functionality
 * @property patch An update that represents bug fixes
 */
data class ModuleVersion(val major: Int, val minor: Int, val patch: Int) {

  /** Formatted as `MAJOR.MINOR.PATCH` */
  override fun toString() = "$major.$minor.$patch"

  companion object {

    /**
     * Extrapolate the version variables from a provided [String], and turns them into a
     * [ModuleVersion].
     *
     * The String should be in the format of `MAJOR.MINOR.PATCH`.
     *
     * ```
     * ModuleVersion.fromString("1.2.3") // ModuleVersion(1,2,3)
     * ModuleVersion.fromString("a.b.c") // IllegalArgumentException
     * ```
     *
     * @param str a [String] that matches the `MAJOR.MINOR.PATCH` format.
     *
     * @throws IllegalArgumentException on invalid format.
     *
     * @see fromStringOrNull
     */
    fun fromString(str: String): ModuleVersion =
      runCatching {
          val (major, minor, patch) = str.split(".").map { it.toInt() }

          return ModuleVersion(major, minor, patch)
        }
        .getOrElse { throw IllegalArgumentException("Invalid format for provided version.", it) }

    /**
     * Runs [ModuleVersion.fromString], but catches any exceptions and converts them into null.
     *
     * ```
     * ModuleVersion.fromString("1.2.3") // ModuleVersion(1,2,3)
     * ModuleVersion.fromString("a.b.c") // null
     * ```
     *
     * @param str a [String] that matches the `MAJOR.MINOR.PATCH` format.
     */
    fun fromStringOrNull(str: String): ModuleVersion? = runCatching { fromString(str) }.getOrNull()
  }

  /**
   * Returns a copy of this [ModuleVersion], with the given [VersionType] increased by one.
   *
   * @param version the [VersionType] to increase; defaults to [VersionType.PATCH]
   */
  fun bump(version: VersionType = VersionType.PATCH) =
    when (version) {
      VersionType.MAJOR -> copy(major = major + 1)
      VersionType.MINOR -> copy(minor = minor + 1)
      VersionType.PATCH -> copy(patch = patch + 1)
    }
}
