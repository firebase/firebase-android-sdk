// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.functions

/**
 * Options for configuring the callable function.
 *
 * These properties are immutable once a callable function reference is instantiated.
 */
public class HttpsCallableOptions
private constructor(
  /**
   * Returns the setting indicating if limited-use App Check tokens are enforced for this function.
   */
  // If true, request a limited-use token from AppCheck.
  @JvmField public val limitedUseAppCheckTokens: Boolean,
  @JvmField internal val headers: Map<String, String>,
) {

  public fun getLimitedUseAppCheckTokens(): Boolean {
    return limitedUseAppCheckTokens
  }

  public fun getHeaders(): Map<String, String> {
    // Returning a defensive copy
    return headers.toMap()
  }

  /** A builder for creating [com.google.firebase.functions.HttpsCallableOptions]. */
  public class Builder {
    @JvmField public var limitedUseAppCheckTokens: Boolean = false
    private val headers: MutableMap<String, String> = mutableMapOf()

    /** Returns the setting indicating if limited-use App Check tokens are enforced. */
    public fun getLimitedUseAppCheckTokens(): Boolean {
      return limitedUseAppCheckTokens
    }

    /**
     * Sets whether or not to use limited-use App Check tokens when invoking the associated
     * function.
     */
    public fun setLimitedUseAppCheckTokens(limitedUse: Boolean): Builder {
      limitedUseAppCheckTokens = limitedUse
      return this
    }

    /**
     * Adds an HTTP header for callable functions.
     *
     * Note that an existing header with the same name will be overwritten.
     *
     * @param name Name of HTTP header
     * @param value Value of HTTP header
     */
    public fun addHeader(name: String, value: String): Builder {
      headers[name] = value
      return this
    }

    /**
     * Adds all HTTP headers of passed map for callable functions.
     *
     * Note that an existing header with the same name will be overwritten.
     *
     * @param headers Map of HTTP headers (name to value)
     */
    public fun addHeaders(headers: Map<String, String>): Builder {
      this.headers.putAll(headers)
      return this
    }

    /** Builds a new [com.google.firebase.functions.HttpsCallableOptions]. */
    public fun build(): HttpsCallableOptions {
      return HttpsCallableOptions(limitedUseAppCheckTokens, headers.toMap())
    }
  }
}
