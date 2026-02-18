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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.NullableReference
import io.kotest.property.Arb
import io.kotest.property.RTree
import io.kotest.property.RandomSource
import io.kotest.property.Sample
import io.kotest.property.arbitrary.orNull
import io.kotest.property.asSample
import io.kotest.property.map

/**
 * Returns an [Arb] that produces the same samples and edge cases as `this` except that the values
 * are wrapped in [NullableReference]. Samples are `NullableReference(null)` with a probability of
 * the given [nullProbability], which must be between `0.0` and `1.0`, inclusive; otherwise, a
 * [NullableReference] of a sample from `this` is used.
 *
 * This is functionally equivalent to the standard [orNull] function, except that it works around
 * the problem that [Arb.edgecase] returning `null` indicates that _no_ edge cases are supported,
 * rather than `null` itself being an edge case.
 *
 * See https://github.com/kotest/kotest/issues/4029 for details.
 */
fun <T> Arb<T>.orNullableReference(nullProbability: Double): Arb<NullableReference<T>> =
  NullableReferenceArb(this, nullProbability)

private class NullableReferenceArb<out T>(
  private val arb: Arb<T>,
  private val nullProbability: Double,
) : Arb<NullableReference<T>>() {
  init {
    require(nullProbability in 0.0..1.0) {
      "invalid nullProbability: $nullProbability (must be between 0.0 and 1.0, inclusive)"
    }
  }

  override fun sample(rs: RandomSource): Sample<NullableReference<T>> {
    if (rs.isNextNull()) {
      return NullableReference<T>(null).asSample()
    }

    val baseSample = arb.sample(rs)
    val wrappedShrinks = baseSample.shrinks.map { NullableReference(it) }

    val shrinks =
      if (nullProbability == 0.0 || baseSample.value === null) {
        wrappedShrinks
      } else {
        // This logic was adapted from the implementation of io.kotest.property.arbitrary.orNull.
        RTree(
          wrappedShrinks.value,
          lazy { listOf(RTree({ NullableReference(null) })) + wrappedShrinks.children.value }
        )
      }

    return Sample(NullableReference(baseSample.value), shrinks)
  }

  override fun edgecase(rs: RandomSource): NullableReference<T>? {
    if (rs.isNextNull()) {
      return NullableReference(null)
    }

    val edgeCase = arb.edgecase(rs)

    // Return null if, and only if, the underlying Arb does not support edge cases _and_ the
    // nullProbability is 0.0, because in this specific case there are no edge cases to return.
    if (edgeCase === null && nullProbability == 0.0) {
      return null
    }

    return NullableReference(edgeCase)
  }

  private fun RandomSource.isNextNull(): Boolean =
    when (nullProbability) {
      0.0 -> false
      1.0 -> true
      else -> random.nextDouble() <= nullProbability
    }
}
