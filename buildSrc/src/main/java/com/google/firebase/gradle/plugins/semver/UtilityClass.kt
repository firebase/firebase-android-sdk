package com.google.firebase.gradle.plugins.semver

import java.util.*

object UtilityClass {
  fun isObfuscatedSymbol(symbol: String): Boolean {
    val normalizedSymbol = symbol.toLowerCase(Locale.ROOT).replace("\\[\\]", "")
    return normalizedSymbol.startsWith("zz") || normalizedSymbol.startsWith("za")
  }
}
