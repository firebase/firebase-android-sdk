/*
 * Copyright 2024 Google LLC
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

package com.google.firebase.appdistribution.gradle

import com.google.common.base.Charsets
import com.google.common.base.Splitter
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

/** Util class supporting common operations for parsing file paths and properties. */
object OptionsUtils {
  @JvmStatic
  fun splitCommaOrNewlineSeparatedString(str: String?): List<String> {
    return if (str == null) emptyList()
    else Splitter.on(Pattern.compile("[,\n]")).omitEmptyStrings().trimResults().splitToList(str)
  }

  fun splitSemicolonOrNewlineSeparatedString(str: String?): List<String> {
    return if (str == null) emptyList()
    else Splitter.on(Pattern.compile("[;\n]")).omitEmptyStrings().trimResults().splitToList(str)
  }

  @JvmStatic
  fun getValueFromStringOrFile(value: String?, path: String?): String? {
    return if (value.isNullOrEmpty() && !path.isNullOrEmpty()) {
      try {
        Files.asCharSource(File(path), Charsets.UTF_8).read()
      } catch (e: IOException) {
        throw IllegalArgumentException("Failed to read file \"$path\"", e)
      }
    } else value
  }

  @JvmStatic
  fun ensureFileExists(path: String?, missingFileReason: AppDistributionException.Reason): File {
    val file = File(path)
    if (!file.exists()) {
      throw AppDistributionException(missingFileReason)
    }
    return file
  }
}
