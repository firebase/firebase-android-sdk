package com.google.firebase.gradle.plugins

/**
 * Replaces all matching substrings with an empty string (nothing)
 */
fun String.remove(regex: Regex) = replace(regex, "")

/**
 * Replaces all matching substrings with an empty string (nothing)
 */
fun String.remove(str: String) = replace(str, "")
