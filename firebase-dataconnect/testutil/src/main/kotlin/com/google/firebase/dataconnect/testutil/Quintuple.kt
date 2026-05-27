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

package com.google.firebase.dataconnect.testutil

import java.io.Serializable

// Note: the code below was adapted from the Triple class in the Kotlin standard library.

data class Quintuple<out A, out B, out C, out D, out E>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D,
  val fifth: E
) : Serializable {

  /**
   * Returns string representation of the [Quintuple] including its [first], [second], [third],
   * [fourth], and [fifth] values.
   */
  override fun toString(): String = "($first, $second, $third, $fourth, $fifth)"
}

/** Converts this quintuple into a list. */
fun <T> Quintuple<T, T, T, T, T>.toList(): List<T> = listOf(first, second, third, fourth, fifth)
