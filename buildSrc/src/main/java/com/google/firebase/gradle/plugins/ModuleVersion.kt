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

/**
 * Possible version types in a SemVer versioning scheme.
 *
 * @see ModuleVersion
 */
enum class VersionType {
  MAJOR,
  MINOR,
  PATCH,
  PRE
}

/**
 * Type-Safe representation of pre-release identifiers.
 *
 * The following priority is established, in order of newest to oldest:
 *
 * `RC > EAP > BETA > ALPHA`
 *
 * @see PreReleaseVersion
 */
enum class PreReleaseVersionType {
  ALPHA,
  BETA,
  EAP,
  RC
}

/**
 * Type-Safe representation of pre-release [Versions][VersionType] for [ModuleVersion].
 *
 * Pre-Release versions (otherwise known as [pre][VersionType.PRE]) indicates changes that are worth
 * releasing- but not stable enough to satisfy a full release.
 *
 * Pre-Release versions should be in the format of:
 *
 * `(Type)(Build)`
 *
 * Where `Type` is a case insensitive string of any [PreReleaseVersionType], and `Build` is a two
 * digit number (single digits should have a leading zero).
 *
 * Note that `build` will always be present as starting at one by defalt. That is, the following
 * transform occurs:
 * ```
 * "12.13.1-beta" // 12.13.1-beta01
 * ```
 *
 * @property type an enum of [PreReleaseVersionType] that identifies the pre-release identifier
 * @property build an [Int] that specifies the build number; defaults to one
 * @see fromStringsOrNull
 */
data class PreReleaseVersion(val type: PreReleaseVersionType, val build: Int = 1) :
  Comparable<PreReleaseVersion> {

  companion object {

    /**
     * Converts a string of [type] and [build] into a valid [PreReleaseVersion].
     *
     * To learn more about what is considered a valid string and not, take a look at
     * [PreReleaseVersion].
     *
     * Example Usage:
     * ```
     * PreReleaseVersion.fromStringsOrNull("alpha", "6") // PreReleaseVersion(ALPHA, 6)
     * PreReleaseVersion.fromStringsOrNull("Beta", "") // PreReleaseVersion(BETA, 1)
     * PreReleaseVersion.fromStringsOrNull("", "13") // null
     * ```
     *
     * @param type a case insensitive string of any [PreReleaseVersionType]
     * @param build a string number; gets automatically converted to double digits, and defaults to
     * one if blank
     * @return a [PreReleaseVersion] created from the string, or null if the string was invalid.
     */
    fun fromStringsOrNull(type: String, build: String): PreReleaseVersion? =
      runCatching {
          val preType = PreReleaseVersionType.valueOf(type.toUpperCase())
          val buildNumber = build.takeUnless { it.isBlank() }?.toInt() ?: 1

          PreReleaseVersion(preType, buildNumber)
        }
        .getOrNull()
  }

  override fun compareTo(other: PreReleaseVersion) =
    compareValuesBy(this, other, { it.type }, { it.build })

  /** Returns a copy of this [PreReleaseVersion], with the [build] increased by one. */
  fun bump() = copy(build = build + 1)

  /**
   * Formatted as `TypeBuild`
   *
   * For example:
   * ```
   * PreReleaseVersion(ALPHA, 5).toString() // "alpha05"
   * PreReleaseVersion(RC, 12).toString() // "rc12"
   * ```
   */
  override fun toString() = "${type.name.toLowerCase()}${build.toString().padStart(2, '0')}"
}

/**
 * Type-Safe representation of your standard [SemVer](https://semver.org/) versioning scheme.
 *
 * All versions should fall under your standard `MAJOR.MINOR.PATCH-PRE` format, where `PRE` is
 * optional.
 *
 * To see rules about pre-release (`PRE`) formatting, see [PreReleaseVersion].
 *
 * @property major An update that represents breaking changes
 * @property minor An update that represents new functionality
 * @property patch An update that represents bug fixes
 * @property pre An update that represents unstable changes not ready for a full release
 * @see fromStringOrNull
 */
data class ModuleVersion(
  val major: Int,
  val minor: Int,
  val patch: Int,
  val pre: PreReleaseVersion? = null
) : Comparable<ModuleVersion> {

  /** Formatted as `MAJOR.MINOR.PATCH-PRE` */
  override fun toString() = "$major.$minor.$patch${pre?.let { "-${it.toString()}" } ?: ""}"

  override fun compareTo(other: ModuleVersion) =
    compareValuesBy(
      this,
      other,
      { it.major },
      { it.minor },
      { it.patch },
      { it.pre == null }, // a version with no prerelease version takes precedence
      { it.pre }
    )

  companion object {
    /**
     * Regex used in matching SemVer versions.
     *
     * The regex can be broken down as such:
     *
     * `(N digits).(N digits).(N digits)-(maybe letters)(maybe numbers)`
     *
     * For example, the following would be valid matches:
     * ```
     * "13.1.5" // valid
     * "5.0.5-beta" // valid
     * "19.45.12-rc09" // valid
     * ```
     *
     * While the following would not be:
     * ```
     * "1.3.4-" // invalid
     * "16.2.3-01" // invalid
     * "5.1.c" // invalid
     * ```
     */
    val VERSION_REGEX =
      "(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)(?:\\-\\b)?(?<pre>\\w\\D+)?(?<build>\\B\\d+)?".toRegex()

    /**
     * Extrapolates the version variables from a provided [String], and turns them into a
     * [ModuleVersion].
     *
     * The String should be in the format of `MAJOR.MINOR.PATCH-PRE`.
     *
     * ```
     * ModuleVersion.fromString("1.2.3") // ModuleVersion(1,2,3)
     * ModuleVersion.fromString("16.12.3-alpha") // ModuleVersion(16,12,3, ...)
     * ModuleVersion.fromString("5.4.1-beta01") // ModuleVersion(5,4,1, ...)
     * ModuleVersion.fromString("a.b.c") // null
     * ```
     *
     * @param str a [String] that matches the SemVer format.
     * @return a [ModuleVersion] created from the string, or null if the string was invalid.
     */
    fun fromStringOrNull(str: String): ModuleVersion? =
      runCatching {
          VERSION_REGEX.matchEntire(str)?.let {
            val (major, minor, patch, pre, build) = it.destructured
            ModuleVersion(
                major.toInt(),
                minor.toInt(),
                patch.toInt(),
                PreReleaseVersion.fromStringsOrNull(pre, build)
              )
              .takeUnless { it.pre == null && (pre.isNotEmpty() || build.isNotEmpty()) }
          }
        }
        .getOrNull()
  }

  /**
   * Returns a copy of this [ModuleVersion], with the given [VersionType] increased by one.
   *
   * @param version the [VersionType] to increase; defaults to the lowest valid version ([pre] else
   * [patch]).
   */
  fun bump(version: VersionType? = null) =
    version
      .let { it ?: if (pre != null) VersionType.PRE else VersionType.PATCH }
      .let {
        when (it) {
          VersionType.MAJOR -> copy(major = major + 1)
          VersionType.MINOR -> copy(minor = minor + 1)
          VersionType.PATCH -> copy(patch = patch + 1)
          VersionType.PRE -> copy(pre = pre?.bump())
        }
      }
}
