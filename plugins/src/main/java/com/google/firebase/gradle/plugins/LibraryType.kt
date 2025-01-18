package com.google.firebase.gradle.plugins

/**
 * The type of a [FirebaseLibraryExtension].
 *
 * @property format The file format that this library publishes (eg; `aar` or `jar).
 */
enum class LibraryType(val format: String) {
  ANDROID("aar"),
  JAVA("jar");

  companion object {
    fun fromFormat(format: String): LibraryType =
      LibraryType.values().find { it.format == format }
        ?: throw IllegalArgumentException("Library type not found for format: '$format'")
  }
}

/**
 * The name of the maven component that this library should use.
 *
 * Due to the fact that multiple components are created for android libraries (1 per variant), the
 * "android" component contains artifacts from all 3 variants for Kotlin libraries, which is invalid
 * (bug in https://github.com/wupdigital/android-maven-publish ?). So we explicitly choose the
 * "Release" variant for android libraries.
 */
val LibraryType.componentName: String
  get() = if (this == LibraryType.ANDROID) "release" else name.lowercase()
