/*
 * Copyright 2026 Google LLC
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
package com.google.firebase.dataconnect.util

import java.math.BigInteger

/**
 * Holder for "global" functions related to [java.math.BigInteger].
 *
 * Technically, these functions _could_ be defined as free functions; however, doing so creates a
 * BigIntegerUtilKt Java class with public visibility, which pollutes the public API. Using an
 * "internal" object, instead, to gather together the top-level functions avoids this public API
 * pollution.
 */
internal object BigIntegerUtil {

  private val LONG_MAX_VALUE_BIG_INTEGER = Long.MAX_VALUE.toBigInteger()

  private val LONG_MIN_VALUE_BIG_INTEGER = Long.MIN_VALUE.toBigInteger()

  /**
   * Clamps this [BigInteger] to be within the range of [Long.MIN_VALUE] and [Long.MAX_VALUE].
   *
   * If the [BigInteger] is less than [Long.MIN_VALUE], returns [Long.MIN_VALUE]. If the
   * [BigInteger] is greater than [Long.MAX_VALUE], returns [Long.MAX_VALUE]. Otherwise, it will
   * return the [BigInteger] converted to a [Long] with the same value.
   *
   * @return This [BigInteger] as a [Long], clamped to the range of [Long].
   */
  fun BigInteger.clampToLong(): Long =
    coerceIn(LONG_MIN_VALUE_BIG_INTEGER, LONG_MAX_VALUE_BIG_INTEGER).toLong()
}
