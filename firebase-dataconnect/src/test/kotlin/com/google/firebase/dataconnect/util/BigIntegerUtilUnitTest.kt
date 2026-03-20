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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.util.BigIntegerUtil.clampToLong
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.positiveLong
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class BigIntegerUtilUnitTest {

  @Test
  fun `clampToLong() returns the exact value if within the range of Long`() = runTest {
    checkAll(propTestConfig, Arb.long()) { long ->
      val bigInteger = long.toBigInteger()

      bigInteger.clampToLong() shouldBe long
    }
  }

  @Test
  fun `clampToLong() returns MAX_VALUE if greater than MAX_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.positiveLong()) { delta ->
      val bigInteger = Long.MAX_VALUE.toBigInteger() + delta.toBigInteger()

      bigInteger.clampToLong() shouldBe Long.MAX_VALUE
    }
  }

  @Test
  fun `clampToLong() returns MIN_VALUE if greater than MIN_VALUE`() = runTest {
    checkAll(propTestConfig, Arb.positiveLong()) { delta ->
      val bigInteger = Long.MIN_VALUE.toBigInteger() - delta.toBigInteger()

      bigInteger.clampToLong() shouldBe Long.MIN_VALUE
    }
  }
}

private val propTestConfig = PropTestConfig(iterations = 1000)
