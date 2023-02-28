package com.google.firebase.gradle.plugins

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
     * Uses Regex to extrapolate the version variables from a provided [String], and turns them into
     * a [ModuleVersion].
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

  /** Returns a copy of this [ModuleVersion], with the [patch] increased by one. */
  fun bump() = copy(patch = patch + 1)
}
