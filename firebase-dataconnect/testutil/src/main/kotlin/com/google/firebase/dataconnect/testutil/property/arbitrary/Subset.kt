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

/**
 * Returns an [Arb] that generates random subsets of the given set.
 *
 * The elements of the set must be [Comparable] so that the set can be sorted into a deterministic
 * order before shuffling. Because the iteration order of a generic [Set] is unspecified, sorting is
 * necessary to guarantee that property-based tests remain reproducible when run with a fixed random
 * seed.
 *
 * @param T The type of elements in the set.
 * @param values The set of values from which to choose.
 * @param minSize The minimum size of the generated subsets (inclusive); must be in the range
 * `0..values.size`; defaults to 0; must be less than or equal to [maxSize].
 * @param maxSize The maximum size of the generated subsets (inclusive); must be in the range
 * `0..values.size`; defaults to `values.size`; must be greater than or equal to [minSize].
 * @return An [Arb] that generates random sets whose size is greater than or equal to [minSize] and
 * less than or equal to [maxSize] containing elements from [values].
 * @throws IllegalArgumentException if [minSize] and/or [maxSize] violate their documented
 * constraints.
 */
fun <T : Comparable<T>> Arb.Companion.subset(
  values: Set<T>,
  minSize: Int = 0,
  maxSize: Int = values.size,
): Arb<Set<T>> {
  val validSizeRange = 0..values.size
  require(minSize in validSizeRange) { "invalid minSize: $minSize (must be in $validSizeRange)" }
  require(maxSize in validSizeRange) { "invalid maxSize: $maxSize (must be in $validSizeRange)" }
  require(minSize <= maxSize) {
    "minSize ($minSize) is greater than maxSize ($maxSize), " +
      "but minSize must be less than or equal to maxSize"
  }

  return Arb.bind(Arb.shuffle(values.sorted()), Arb.int(minSize..maxSize)) { shuffledValues, size ->
    shuffledValues.take(size).toSet()
  }
}
