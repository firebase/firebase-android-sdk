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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.shuffle
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

/**
 * Returns an [Arb] that generates random subsets of entries of the given enum.
 *
 * @param T The enum type.
 * @param entries The entries to choose from. Defaults to all entries of the enum.
 * @param minSize The minimum size of the generated sets (inclusive); must be in the range
 * `0..entries.size`; defaults to 0; must be less than or equal to [maxSize].
 * @param maxSize The maximum size of the generated sets (inclusive); must be in the range
 * `0..entries.size`; defaults to `entries.size`; must be greater than or equal to [minSize].
 * @return An [Arb] that generates random sets whose size is greater than or equal to [minSize] and
 * less than or equal to [maxSize] of enum entries from [entries].
 * @throws IllegalArgumentException if [minSize] and/or [maxSize] violate their documented
 * constraints.
 */
inline fun <reified T : Enum<T>> Arb.Companion.enumSubset(
  entries: EnumEntries<T> = enumEntries(),
  minSize: Int = 0,
  maxSize: Int = entries.size,
): Arb<Set<T>> {
  val validSizeRange = 0..entries.size
  require(minSize in validSizeRange) { "invalid minSize: $minSize (must be in $validSizeRange)" }
  require(maxSize in validSizeRange) { "invalid maxSize: $maxSize (must be in $validSizeRange)" }
  require(minSize <= maxSize) {
    "minSize ($minSize) is greater than maxSize ($maxSize), " +
      "but minSize must be less than or equal to maxSize"
  }

  return Arb.bind(Arb.shuffle(entries), Arb.int(minSize..maxSize)) { shuffledEntries, size ->
    shuffledEntries.take(size).toSet()
  }
}
