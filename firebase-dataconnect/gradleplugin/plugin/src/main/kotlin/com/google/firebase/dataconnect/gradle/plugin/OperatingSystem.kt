/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.gradle.plugin

import java.util.Locale

enum class OperatingSystem(val executableSuffix: String) {
  Windows(".exe"),
  MacOS(""),
  Linux("");

  companion object {
    fun current(): OperatingSystem? {
      val osName = System.getProperty("os.name")
      return if (osName === null) null else forName(osName)
    }

    fun forName(os: String): OperatingSystem? = forNameWithLowercaseOS(os.lowercase(Locale.ROOT))

    // This logic was adapted from
    // https://github.com/gradle/gradle/blob/4457734e73/platforms/core-runtime/base-services/src/main/java/org/gradle/internal/os/OperatingSystem.java#L64-L82
    private fun forNameWithLowercaseOS(os: String): OperatingSystem? =
      if (os.contains("windows")) {
        Windows
      } else if (os.contains("mac os x") || os.contains("darwin") || os.contains("osx")) {
        MacOS
      } else if (os.contains("linux")) {
        Linux
      } else {
        null
      }
  }
}
