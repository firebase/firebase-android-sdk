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

package com.google.firebase.dataconnect.sqlite

import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.negativeInt
import io.kotest.property.arbitrary.negativeLong
import io.kotest.property.arbitrary.withEdgecases

object CodedIntegersTesting {

  val uint32MaxValueByByteCount: List<Int> = listOf(0, 127, 16_383, 2_097_151, 268_435_455)

  val uint32EdgeCases: List<Int> =
    uint32MaxValueByByteCount
      .flatMap { listOf(it, -it) }
      .flatMap { listOf(it, it + 1, it + 2, it - 1) }
      .toMutableList()
      .apply {
        add(1)
        add(-1)
        add(2)
        add(-2)
      }
      .distinct()

  fun uint32Arb(): Arb<Int> {
    val positiveRanges =
      uint32MaxValueByByteCount.windowed(2, partialWindows = true).map { window ->
        when (window.size) {
          1 -> (window[0] + 1)..Int.MAX_VALUE
          2 -> (window[0] + 1)..window[1]
          else ->
            throw IllegalStateException("window.size=${window.size} window=$window [rv27chgadc]")
        }
      }

    val arbs =
      positiveRanges.map { Arb.int(it) }.toMutableList().apply { add(Arb.negativeInt()) }.toList()

    return Arb.choice(arbs).withEdgecases(uint32EdgeCases)
  }

  fun Int.calculateUInt32Size(): Int {
    if (this < 0) {
      return 5
    } else if (this == 0) {
      return 1
    }
    uint32MaxValueByByteCount.forEachIndexed { index, cutoff ->
      if (this <= cutoff) {
        return index
      }
    }
    return 5
  }

  val uint64MaxValueByByteCount: List<Long> =
    listOf(
      0,
      127,
      16_383,
      2_097_151,
      268_435_455,
      34_359_738_367,
      4_398_046_511_103,
      562_949_953_421_311,
      72_057_594_037_927_935,
    )

  val uint64EdgeCases: List<Long> =
    uint64MaxValueByByteCount
      .flatMap { listOf(it, -it) }
      .flatMap { listOf(it, it + 1, it + 2, it - 1) }
      .toMutableList()
      .apply {
        add(1)
        add(-1)
        add(2)
        add(-2)
      }
      .distinct()

  fun uint64Arb(): Arb<Long> {
    val positiveRanges =
      uint64MaxValueByByteCount.windowed(2, partialWindows = true).map { window ->
        when (window.size) {
          1 -> (window[0] + 1)..Long.MAX_VALUE
          2 -> (window[0] + 1)..window[1]
          else ->
            throw IllegalStateException("window.size=${window.size} window=$window [f4t2gk6tbb]")
        }
      }

    val arbs =
      positiveRanges.map { Arb.long(it) }.toMutableList().apply { add(Arb.negativeLong()) }.toList()

    return Arb.choice(arbs).withEdgecases(uint64EdgeCases)
  }

  fun Long.calculateUInt64Size(): Int {
    if (this < 0) {
      return 10
    } else if (this == 0L) {
      return 1
    }
    uint64MaxValueByByteCount.forEachIndexed { index, cutoff ->
      if (this <= cutoff) {
        return index
      }
    }
    return 9
  }

  val sint32RangeByByteCount: List<IntRange> =
    listOf(
      -64..63,
      -8192..8191,
      -1_048_576..1_048_575,
      -134_217_728..134_217_727,
      Int.MIN_VALUE..Int.MAX_VALUE,
    )

  val sint32EdgeCases: List<Int> =
    sint32RangeByByteCount
      .flatMap { listOf(it.first, it.last) }
      .flatMap { listOf(it, it + 1, it + 2, it - 1, it - 2) }
      .toMutableList()
      .apply {
        add(0)
        add(1)
        add(-1)
        add(2)
        add(-2)
      }
      .distinct()

  fun sint32Arb(): Arb<Int> {
    val ranges =
      sint32RangeByByteCount
        .reversed()
        .windowed(2, partialWindows = true)
        .map { window ->
          val range1: IntRange = window[0]
          when (window.size) {
            1 -> listOf(range1, range1)
            2 -> {
              val range2: IntRange = window[1]
              listOf(range1.first until range2.first, (range2.last + 1)..range1.last)
            }
            else ->
              throw IllegalStateException("window.size=${window.size} window=$window [w2gj722j6v]")
          }
        }
        .flatten()

    val arbs = ranges.map { Arb.int(it) }
    return Arb.choice(arbs).withEdgecases(sint32EdgeCases)
  }

  fun Int.calculateSInt32Size(): Int {
    sint32RangeByByteCount.forEachIndexed { index, range ->
      if (this in range) {
        return index + 1
      }
    }
    throw IllegalStateException("should never get here yxwcs2ndgh: $this")
  }

  val sint64RangeByByteCount: List<LongRange> =
    listOf(
      -64L..63L,
      -8192L..8191L,
      -1_048_576L..1_048_575L,
      -134_217_728L..134_217_727L,
      -17_179_869_184..17_179_869_183,
      -2_199_023_255_552..2_199_023_255_551,
      -281_474_976_710_656..281_474_976_710_655,
      -36_028_797_018_963_968..36_028_797_018_963_967,
      -4_611_686_018_427_387_904..4_611_686_018_427_387_903,
      Long.MIN_VALUE..Long.MAX_VALUE,
    )

  val sint64EdgeCases: List<Long> =
    sint64RangeByByteCount
      .flatMap { listOf(it.first, it.last) }
      .flatMap { listOf(it, it + 1, it + 2, it - 1, it - 2) }
      .toMutableList()
      .apply {
        add(0)
        add(1)
        add(-1)
        add(2)
        add(-2)
      }
      .distinct()

  fun sint64Arb(): Arb<Long> {
    val ranges =
      sint64RangeByByteCount
        .reversed()
        .windowed(2, partialWindows = true)
        .map { window ->
          val range1: LongRange = window[0]
          when (window.size) {
            1 -> listOf(range1, range1)
            2 -> {
              val range2: LongRange = window[1]
              listOf(range1.first until range2.first, (range2.last + 1)..range1.last)
            }
            else ->
              throw IllegalStateException("window.size=${window.size} window=$window [w2gj722j6v]")
          }
        }
        .flatten()

    val arbs = ranges.map { Arb.long(it) }
    return Arb.choice(arbs).withEdgecases(sint64EdgeCases)
  }

  fun Long.calculateSInt64Size(): Int {
    sint64RangeByByteCount.forEachIndexed { index, range ->
      if (this in range) {
        return index + 1
      }
    }
    throw IllegalStateException("should never get here p5zq8wjntk: $this")
  }
}
