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

enum class CpuArchitecture {
  AMD64,
  ARM64;

  companion object {
    fun current(): CpuArchitecture? {
      val arch = System.getProperty("os.arch")
      return if (arch === null) null else forName(arch)
    }

    fun forName(arch: String): CpuArchitecture? =
      forNameWithLowercaseArch(arch.lowercase(Locale.ROOT))

    // This logic was adapted from
    // https://github.com/gradle/gradle/blob/4457734e73fc567a43ccf96185341432b636bc47/platforms/core-runtime/base-services/src/main/java/org/gradle/internal/os/OperatingSystem.java#L357-L369
    private fun forNameWithLowercaseArch(arch: String): CpuArchitecture? =
      when (arch) {
        "x86_64",
        "amd64" -> CpuArchitecture.AMD64
        "aarch64" -> CpuArchitecture.ARM64
        else -> null
      }
  }
}
