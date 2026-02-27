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
@file:OptIn(ExperimentalKotest::class)

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.fourValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.sorted
import com.google.firebase.dataconnect.testutil.property.arbitrary.threeValues
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RangeIntersectUnitTest {

  @Test
  fun `IntRange intersect with identical range`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.int())) { values ->
      val (first, last) = values.sorted()
      val range1 = first..last
      val range2 = first..last

      (range1 intersect range2) shouldBe range1
      (range2 intersect range1) shouldBe range1
    }
  }

  @Test
  fun `LongRange intersect with identical range`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.long())) { values ->
      val (first, last) = values.sorted()
      val range1 = first..last
      val range2 = first..last

      (range1 intersect range2) shouldBe range1
      (range2 intersect range1) shouldBe range1
    }
  }

  @Test
  fun `IntRange intersect with full overlap`() = runTest {
    checkAll(propTestConfig, Arb.fourValues(Arb.int())) { values ->
      val (first1, last1, first2, last2) = values.sorted()
      val outerRange = first1..last2
      val innerRange = first2..last1

      (outerRange intersect innerRange) shouldBe innerRange
      (innerRange intersect outerRange) shouldBe innerRange
    }
  }

  @Test
  fun `LongRange intersect with full overlap`() = runTest {
    checkAll(propTestConfig, Arb.fourValues(Arb.long())) { values ->
      val (first1, last1, first2, last2) = values.sorted()
      val outerRange = first1..last2
      val innerRange = first2..last1

      (outerRange intersect innerRange) shouldBe innerRange
      (innerRange intersect outerRange) shouldBe innerRange
    }
  }

  @Test
  fun `IntRange intersect with same first, different last`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.int())) { values ->
      val (first, last1, last2) = values.sorted()
      val smallerRange = first..last1
      val largerRange = first..last2

      (smallerRange intersect largerRange) shouldBe smallerRange
      (largerRange intersect smallerRange) shouldBe smallerRange
    }
  }

  @Test
  fun `LongRange intersect with same first, different last`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.long())) { values ->
      val (first, last1, last2) = values.sorted()
      val smallerRange = first..last1
      val largerRange = first..last2

      (smallerRange intersect largerRange) shouldBe smallerRange
      (largerRange intersect smallerRange) shouldBe smallerRange
    }
  }

  @Test
  fun `IntRange intersect with different first, same last`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.int())) { values ->
      val (first1, first2, last) = values.sorted()
      val largerRange = first1..last
      val smallerRange = first2..last

      (smallerRange intersect largerRange) shouldBe smallerRange
      (largerRange intersect smallerRange) shouldBe smallerRange
    }
  }

  @Test
  fun `LongRange intersect with different first, same last`() = runTest {
    checkAll(propTestConfig, Arb.threeValues(Arb.long())) { values ->
      val (first1, first2, last) = values.sorted()
      val largerRange = first1..last
      val smallerRange = first2..last

      (smallerRange intersect largerRange) shouldBe smallerRange
      (largerRange intersect smallerRange) shouldBe smallerRange
    }
  }

  @Test
  fun `IntRange intersect with empty`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.int())) { values ->
      val (first, last) = values.sorted()
      val range = first..last

      (range intersect IntRange.EMPTY) shouldBe IntRange.EMPTY
      (IntRange.EMPTY intersect range) shouldBe IntRange.EMPTY
    }
  }

  @Test
  fun `LongRange intersect with empty`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.long())) { values ->
      val (first, last) = values.sorted()
      val range = first..last

      (range intersect LongRange.EMPTY) shouldBe LongRange.EMPTY
      (LongRange.EMPTY intersect range) shouldBe LongRange.EMPTY
    }
  }
}

private val propTestConfig = PropTestConfig(iterations = 500)
