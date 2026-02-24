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

/**
 * Calculates the intersection of this range with another [LongRange].
 *
 * This function finds the common overlapping portion between two ranges. If the ranges do not
 * overlap, the empty range is returned.
 */
infix fun LongRange.intersect(other: LongRange): LongRange {
  val start = maxOf(first, other.first)
  val end = minOf(last, other.last)

  return if (start <= end) {
    start..end
  } else {
    LongRange.EMPTY // No overlap
  }
}

/**
 * Calculates the intersection of this range with another [IntRange].
 *
 * This function finds the common overlapping portion between two ranges. If the ranges do not
 * overlap, the empty range is returned.
 */
infix fun IntRange.intersect(other: IntRange): IntRange {
  val start = maxOf(first, other.first)
  val end = minOf(last, other.last)

  return if (start <= end) {
    start..end
  } else {
    IntRange.EMPTY // No overlap
  }
}
