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

package com.google.firebase.crashlytics.buildtools.gradle.extensions

import java.util.Locale

/** Capitalize the first letter of the string, convenient for gradle task names. */
internal fun String.capitalized(): String =
  // This is copied from [org.gradle.configurationcache.extensions.capitalized] to avoid the dep.
  when {
    isEmpty() -> ""
    else ->
      this[0].let { initial ->
        when {
          initial.isLowerCase() -> initial.titlecase(Locale.getDefault()) + substring(1)
          else -> toString()
        }
      }
  }
