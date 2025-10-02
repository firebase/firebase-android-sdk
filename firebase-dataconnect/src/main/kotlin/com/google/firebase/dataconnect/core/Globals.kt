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

package com.google.firebase.dataconnect.core

/**
 * Holder for "global" functions in this package.
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates
 * XXXKt Java classes whose visibility cannot be controlled. Using an "internal" object, instead, to
 * gather together the top-level functions avoids this public API pollution.
 */
internal object Globals {
  @Suppress("SpellCheckingInspection")
  private const val PLACEHOLDER_APP_CHECK_TOKEN = "eyJlcnJvciI6IlVOS05PV05fRVJST1IifQ=="

  /**
   * Returns a new string that is equal to this string but only includes a chunk from the beginning
   * and the end.
   *
   * This method assumes that the contents of this string are an access token. The returned string
   * will have enough information to reason about the access token in logs without giving its value
   * away.
   */
  fun String.toScrubbedAccessToken(): String =
    if (this == PLACEHOLDER_APP_CHECK_TOKEN) {
      "$this (the \"placeholder\" AppCheck token)"
    } else if (length < 30) {
      "<redacted>"
    } else {
      buildString {
        append(this@toScrubbedAccessToken, 0, 6)
        append("<redacted>")
        append(
          this@toScrubbedAccessToken,
          this@toScrubbedAccessToken.length - 6,
          this@toScrubbedAccessToken.length
        )
      }
    }
}
